package kotgres.ksp.Postgres.mapper

import kotgres.annotations.Column
import kotgres.annotations.Delete
import kotgres.annotations.First
import kotgres.annotations.Id
import kotgres.annotations.Limit
import kotgres.annotations.OnConflictFail
import kotgres.annotations.OrderBy
import kotgres.annotations.PostgresRepository
import kotgres.annotations.Query
import kotgres.annotations.Save
import kotgres.annotations.Statement
import kotgres.annotations.Table
import kotgres.annotations.Version
import kotgres.annotations.Where
import kotgres.aux.ColumnDefinition
import kotgres.aux.PostgresType
import kotgres.ksp.common.KotgresException
import kotgres.ksp.common.Pagination
import kotgres.ksp.common.pageableQualifiedName
import kotgres.ksp.common.paginationParameter
import kotgres.ksp.mapper.camelToSnakeCase
import kotgres.ksp.mapper.kotlinTypeToPostgresTypeMapping
import kotgres.ksp.model.db.ColumnMapping
import kotgres.ksp.model.db.TableMapping
import kotgres.ksp.model.klass.AAnnotation
import kotgres.ksp.model.klass.Field
import kotgres.ksp.model.klass.FunctionParameter
import kotgres.ksp.model.klass.Klass
import kotgres.ksp.model.klass.KlassFunction
import kotgres.ksp.model.klass.Nullability
import kotgres.ksp.model.klass.QualifiedName
import kotgres.ksp.model.klass.Type
import kotgres.ksp.model.klass.primitives
import kotgres.ksp.model.repository.ObjectConstructor
import kotgres.ksp.model.repository.QueryMethod
import kotgres.ksp.model.repository.QueryMethodParameter
import kotgres.ksp.model.repository.QueryMethodType
import kotgres.ksp.model.repository.QueryParameter
import kotgres.ksp.model.repository.Repo
import kotgres.ksp.parser.KotlinType
import kotgres.ksp.parser.toQualifiedName

// ex: `:firstName`
val parameterPlaceholderRegex = Regex(":\\w*")

// matches: ' IN (:dates)', with catch group 1: "dates"
val inClauseRegex = "\\W?IN\\W*\\(:(\\w*)\\)".toRegex(RegexOption.IGNORE_CASE)

fun validationErrors(klass: Klass): List<String> {
    val entityKlass = klass.superclassParameter?.klass ?: return emptyList()

    return entityKlass.fields.flatMap { checkFieldTypes(entityKlass, it.type.klass, listOf(it.name)) }
}

private fun checkFieldTypes(rootKlass: Klass, klass: Klass, path: List<String>): List<String> {
    if (KotlinType.of(klass.name) != null) return emptyList()
    if (klass.isEnum) return emptyList()
    if (klass.fields.isEmpty()) return listOf("Unsupported field type [${rootKlass.name}.${path.joinToString(".")}: ${klass.name}]")
    return klass.fields.flatMap { checkFieldTypes(rootKlass, it.type.klass, path + it.name) }
}

private fun Klass.toTableMapping(): TableMapping {
    val tableAnnotation = annotations.singleOrNull { it.name == Table::class.qualifiedName }

    val tableName = tableAnnotation
        ?.parameters?.get("name")
        ?.takeIf { it.isNotEmpty() }
        ?: name.name.camelToSnakeCase()

    val columns = flattenToColumns(this)

    val objectConstructor = if (name != KotlinType.UNIT.qn) objectConstructor(this, columns) else null

    return TableMapping(
        name = tableName,
        klass = this,
        columns = columns,
        objectConstructor = objectConstructor,
        schema = tableAnnotation?.parameters?.get("schema"),
    )
}

private fun objectConstructor(
    klass: Klass,
    columns: List<ColumnMapping>,
    parentField: String? = null,
    path: List<String> = emptyList(),
): ObjectConstructor {
    return when {
        klass.fields.isEmpty() -> {
            val column = columns.singleOrNull { it.path == path }
                ?: error("path: $path, columns: $columns")

            ObjectConstructor.Extractor(
                resultSetGetterName = getterSetterName(column),
                columnName = column.column.name,
                fieldName = parentField,
                fieldType = column.type.klass.name,
                isJson = column.column.type == PostgresType.JSONB,
                isEnum = column.type.klass.isEnum,
                isPrimitive = column.type.klass.name in primitives,
                isNullable = column.type.nullability == Nullability.NULLABLE,
                kotlinType = KotlinType.of(column.type.klass.name),
            )
        }

        else -> {
            ObjectConstructor.Constructor(
                fieldName = parentField,
                className = klass.name,
                nestedFields = klass.fields.map {
                    objectConstructor(
                        it.type.klass,
                        columns,
                        it.name,
                        path + it.name,
                    )
                },
            )
        }
    }
}

fun getterSetterName(column: ColumnMapping): String {
    return if (column.type.klass.isEnum) "String" else KotlinType.of(column.type.klass.name)?.jdbcSetterName
        ?: error("cannot define JDBC getter/setter name for class: ${column.type.klass.name}")
}

fun Klass.toRepo(dbQualifiedName: QualifiedName): Repo {
    val mappedKlass = superclassParameter?.klass?.toTableMapping()

    val queryMethods = toQueryMethods(functions, mappedKlass)


    return Repo(
        superKlass = this,
        queryMethods = queryMethods,
        mappedKlass = mappedKlass,
        belongsToDb = annotations.single { it.name == PostgresRepository::class.qualifiedName }
            .parameters["belongsToDb"]
            ?.takeIf { it.isNotEmpty() }
            ?.toQualifiedName() // TODO looks like it is wrong in KSP
            ?: dbQualifiedName,
    )
}

private fun toQueryMethods(functions: List<KlassFunction>, mappedKlass: TableMapping?): List<QueryMethod> {
    val (custom, dedicated) = functions.partition { isCustomQueryMethod(it) }
    val (saves, notSave) = dedicated.partition { isSaveMethod(it) }
    val (deletes, queries) = notSave.partition { isDeleteMethod(it) }

    if (dedicated.isNotEmpty() && mappedKlass == null) {
        throw KotgresException("Only method with custom @Queries are allowed in standalone repositories")
    }

    return queries.map { it.toQueryMethod(mappedKlass!!) } +
        custom.map { it.toCustomQueryMethod() } +
        saves.map { it.toSaveMethod(mappedKlass!!) } +
        deletes.map { it.toDeleteMethod(mappedKlass!!) }
}

private fun isCustomQueryMethod(it: KlassFunction) =
    it.annotationConfigs.any {
        it.name == Query::class.qualifiedName && !it.parameters["value"].isNullOrEmpty() ||
            it.name == Statement::class.qualifiedName
    }

private fun isDeleteMethod(it: KlassFunction) = it.annotationConfigs
    .any { it.name == Delete::class.qualifiedName } ||
    it.name.startsWith("delete") &&
    it.annotationConfigs.none { it.name == Query::class.qualifiedName || it.name == Save::class.qualifiedName }

private fun isSaveMethod(it: KlassFunction) = it.annotationConfigs.any { it.name == Save::class.qualifiedName } ||
    it.name.startsWith("save") &&
    it.annotationConfigs.none { it.name == Query::class.qualifiedName || it.name == Delete::class.qualifiedName }

private fun KlassFunction.toDeleteMethod(repoMappedKlass: TableMapping): QueryMethod {
    // TODO check no Limit no pageable

    val entityParamter = parameters.size == 1 && parameters.first().type.klass == repoMappedKlass.klass
    val customWhere = annotationConfigs.any { it.name == Where::class.qualifiedName }
    val (whereClause, queryParameters) = when {
        customWhere -> generateWhere(parameters, annotationConfigs.single { it.name == Where::class.qualifiedName })
        entityParamter -> generateWhere2(parameters, repoMappedKlass)
        else -> generateWhere(parameters, repoMappedKlass)
    }

    val deleteClause = """
                DELETE 
                FROM ${repoMappedKlass.fullTableName()}
    """.trimIndent()

    val queryMethodParameters = parameters.map { QueryMethodParameter(it.name, it.type) }

    return QueryMethod(
        name = name,
        query = listOfNotNull(deleteClause, whereClause).joinToString("\n"),
        queryMethodParameters = queryMethodParameters,
        queryParameters = queryParameters,
        returnType = returnType,
        trueReturnType = Type(Klass(KotlinType.UNIT.qn)),
        returnsCollection = false,
        pagination = null,
        objectConstructor = null,
        optimisticallyLocked = repoMappedKlass.columns.any { it.column.isVersion } && entityParamter && !customWhere,
    )
}

private fun TableMapping.fullTableName(): String =
    listOfNotNull(schema, name).joinToString(".") { "\"$it\"" }

private fun KlassFunction.toCustomQueryMethod(): QueryMethod {
    val paginationParameter = paginationParameter()

    val parameters = parameters.filter { it.type.klass.name != pageableQualifiedName }

    // TODO check statement returns UNIT
    var query = when {
        annotationConfigs.any { it.name == Query::class.qualifiedName } -> (annotationConfigs.single { it.name == Query::class.qualifiedName }).parameters["value"]!!
        annotationConfigs.any { it.name == Statement::class.qualifiedName } -> (annotationConfigs.single { it.name == Statement::class.qualifiedName }).parameters["value"]!!
        else -> throw KotgresException("Cannot find none of [@Query, @Statement]. Function: $this")
    }

    val parametersByName = parameters.associateBy { it.name }

    val queryParametersOrdered = parameterPlaceholderRegex
        .findAll(query)
        .map { it.value.substringAfter(":") }
        .map {
            parametersByName[it] ?: error("Function '$this' does not have parameter '$it' found in query: <$query>")
        }
        .toList()

    (parameters - queryParametersOrdered).takeIf { it.isNotEmpty() }
        ?.let { error("unused parameters: $it, function '$name'") }

    val returnsCollection = returnType.klass.name == QualifiedName("kotlin.collections", "List")

    val trueReturnType = if (returnsCollection || paginationParameter != null) {
        returnType.typeParameters.single()
    } else {
        returnType
    }

    val kotlinType = KotlinType.of(trueReturnType.klass.name)
    val isScalar = kotlinType != null || trueReturnType.klass.isEnum

    val constructor = when {
        kotlinType == KotlinType.UNIT -> null
        isScalar -> {
            ObjectConstructor.Extractor(
                resultSetGetterName = if (trueReturnType.klass.isEnum) "String" else kotlinType!!.jdbcSetterName!!,
                columnName = "N/A", // TODO introduce another class?
                fieldName = null,
                fieldType = if (trueReturnType.klass.isEnum) trueReturnType.klass.name else kotlinType!!.qn,
                isJson = false, // TODO
                isEnum = trueReturnType.klass.isEnum,
                isPrimitive = trueReturnType.klass.name in primitives,
                isNullable = trueReturnType.nullability == Nullability.NULLABLE,
                kotlinType = kotlinType,
            )
        }

        else -> trueReturnType.klass.toTableMapping().objectConstructor
    }

    val limitClause = when {
        returnType.klass.name == KotlinType.UNIT.qn -> null
        returnsCollection -> annotationConfigs.singleOrNull { it.name == Limit::class.qualifiedName }?.parameters?.get("value")
            ?.let { "LIMIT $it" }

        paginationParameter != null -> "LIMIT ? OFFSET ?"
        annotationConfigs.any { it.name == First::class.qualifiedName } -> "LIMIT 1"
        else -> "LIMIT 2"
    }

    val inClauseParams = inClauseRegex.findAll(query).map { it.groupValues[1] }.toList()

    query = inClauseParams.fold(query) { string, param -> string.replace(":$param", "%$param") }

    val queryParameters = queryParametersOrdered
        .filter { it.name !in inClauseParams }
        .mapIndexed { i, it ->
            val convertToArray = it.type.klass.name == KotlinType.LIST.qn
            val postgresType =
                if (convertToArray) kotlinTypeToPostgresTypeMapping[KotlinType.of(it.type.typeParameters.single().klass.name)]
                    ?: PostgresType.NONE else PostgresType.NONE

            QueryParameter(
                path = it.name,
                kotlinType = it.type,
                positionInQuery = i + 1,
                setterName = KotlinType.of(it.type.klass.name)?.jdbcSetterName
                    ?: error("cannot map to KotlinType: ${it.type.klass.name}"),
                isJson = false,
                isEnum = it.type.klass.isEnum,
                isINClause = false,
                convertToArray = convertToArray,
                postgresType = postgresType,
            )
        }

    val inClauseParameters = queryParametersOrdered
        .filter { it.name in inClauseParams }
        .map {
            val convertToArray = it.type.klass.name == KotlinType.LIST.qn
            val PostgresType =
                if (convertToArray) kotlinTypeToPostgresTypeMapping[KotlinType.of(it.type.typeParameters.single().klass.name)]
                    ?: PostgresType.NONE else PostgresType.NONE

            QueryParameter(
                path = it.name,
                kotlinType = it.type,
                positionInQuery = -1,
                setterName = KotlinType.of(it.type.klass.name)?.jdbcSetterName
                    ?: error("cannot map to KotlinType: ${it.type.klass.name}"),
                isJson = false,
                isEnum = it.type.klass.isEnum,
                isINClause = true,
                convertToArray = convertToArray,
                postgresType = PostgresType,
            )
        }

    val paginationQueryParameters = paginationParameter
        ?.let { paginationQueryParameters(it, queryParameters.size) }
        ?: emptyList()

    query = query.replace(parameterPlaceholderRegex, "?")
    if (limitClause != null) {
        query += "\n" + limitClause
    }

    return QueryMethod(
        name = name,
        query = query,
        returnType = returnType,
        trueReturnType = trueReturnType,
        returnsCollection = returnsCollection,
        queryParameters = queryParameters + paginationQueryParameters + inClauseParameters,
        objectConstructor = constructor,
        returnsScalar = isScalar,
        pagination = paginationParameter,
        queryMethodParameters = this.parameters.map { QueryMethodParameter(it.name, it.type) },
        optimisticallyLocked = false,
        isStatement = annotationConfigs.any { it.name == Statement::class.qualifiedName },
    )
}

private fun KlassFunction.toQueryMethod(repoMappedKlass: TableMapping): QueryMethod {
    val paginationParameter = paginationParameter()

    val orderQualifiedName = QualifiedName("kotgres.aux.sort", "Order")
    val orderParam = parameters.singleOrNull { it.type.klass.name == orderQualifiedName }

    val limitParameter = parameters.singleOrNull { it.annotations.any { it.name == Limit::class.qualifiedName } }

    val parameters = parameters.filter {
        it.type.klass.name != pageableQualifiedName &&
            it.type.klass.name != orderQualifiedName &&
            it.annotations.none { it.name == Limit::class.qualifiedName }
    }

    val returnsCollection = returnType.klass.name == QualifiedName("kotlin.collections", "List")

    val trueReturnType = if (returnsCollection || paginationParameter != null) {
        returnType.typeParameters.single()
    } else {
        returnType
    }

    val isCount = returnType.klass.name == KotlinType.INT.qn
    val isExists = returnType.klass.name == KotlinType.BOOLEAN.qn

    val returnKlassTableMapping = if (!isCount && !isExists) trueReturnType.klass.toTableMapping() else null

    val (whereClause, queryParameters) = annotationConfigs
        .singleOrNull { it.name == Where::class.qualifiedName }
        ?.let { generateWhere(parameters, it) }
        ?: generateWhere(parameters, repoMappedKlass)

    val methodAnnotation = annotationConfigs.singleOrNull { it.name == Limit::class.qualifiedName }

    val limitClause = when {
        isCount || isExists -> null
        returnsCollection && limitParameter != null -> "LIMIT ?"
        returnsCollection && methodAnnotation != null -> "LIMIT ${methodAnnotation.parameters["value"]}"
        returnsCollection -> null
        paginationParameter != null -> "LIMIT ? OFFSET ?"
        annotationConfigs.any { it.name == First::class.qualifiedName } -> "LIMIT 1"
        else -> "LIMIT 2"
    }

    val selectClause = when {
        isCount -> """
                SELECT count(*) 
                FROM ${repoMappedKlass.fullTableName()}
                %where
        """.trimIndent()

        isExists -> """
                SELECT EXISTS (
                    SELECT * 
                    FROM ${repoMappedKlass.fullTableName()} 
                    %where
                )
        """.trimIndent()

        else -> """
                SELECT ${returnKlassTableMapping!!.columns.joinToString { "\"${it.column.name}\"" }}
                FROM ${repoMappedKlass.fullTableName()}
                %where
        """.trimIndent()
    }

    val orderByAnnotation = annotationConfigs.singleOrNull { it.name == OrderBy::class.qualifiedName }

    val orderClause = when {
        orderParam != null -> "%orderBy"
        orderByAnnotation != null -> "ORDER BY " + orderByAnnotation.parameters["value"]
        else -> null
    }

    val queryMethodParameters = this.parameters.map { QueryMethodParameter(it.name, it.type) }

    val limitQueryParameters =
        when {
            paginationParameter != null -> paginationQueryParameters(paginationParameter, queryParameters.size)
            limitParameter != null -> listOf(
                QueryParameter(
                    positionInQuery = queryParameters.size + 1,
                    kotlinType = Type(Klass(KotlinType.INT.qn)),
                    setterName = KotlinType.INT.jdbcSetterName!!,
                    path = limitParameter.name,
                    isJson = false,
                    isEnum = false,
                    isINClause = false, // TODO
                    convertToArray = false,
                    postgresType = PostgresType.INTEGER,
                ),
            )

            else -> emptyList()
        }

    val constructor = when {
        isCount || isExists -> {
            val kotlinType = KotlinType.of(trueReturnType.klass.name)!!
            ObjectConstructor.Extractor(
                resultSetGetterName = kotlinType.jdbcSetterName!!,
                columnName = "N/A",
                fieldName = null,
                fieldType = trueReturnType.klass.name,
                isJson = false,
                isEnum = false,
                isPrimitive = true,
                isNullable = trueReturnType.nullability == Nullability.NULLABLE,
                kotlinType = kotlinType,
            )
        }

        else -> returnKlassTableMapping!!.objectConstructor
    }

    return QueryMethod(
        name = name,
        query = listOfNotNull(
            selectClause.replace("(\\n)?%where".toRegex(), whereClause?.let { "\n$it" } ?: ""),
            orderClause,
            limitClause,
        ).joinToString("\n"),
        queryMethodParameters = queryMethodParameters,
        queryParameters = queryParameters + limitQueryParameters,
        returnType = returnType,
        trueReturnType = trueReturnType,
        returnsCollection = returnsCollection,
        pagination = paginationParameter,
        objectConstructor = constructor,
        returnsScalar = isCount || isExists,
        orderParameterName = orderParam?.name,
        optimisticallyLocked = false,
    )
}

private fun generateWhere(
    parameters: List<FunctionParameter>,
    repoMappedKlass: TableMapping,
): Pair<String?, List<QueryParameter>> {
    val columnsByFieldName = repoMappedKlass.columns.associateBy { it.path.last() }

    // todo check parameter types with column types

    fun toCondition(parameter: FunctionParameter): Condition {
        val exactColumn = columnsByFieldName[parameter.name] ?: error("No such column: ${parameter.name}")
        return when {
            exactColumn.type.klass == parameter.type.klass -> Condition(
                exactColumn.column.name,
                nullable = exactColumn.type.nullability == Nullability.NULLABLE && parameter.type.nullability == Nullability.NULLABLE,
                Op.EQ,
                parameter.name,
            )

            parameter.type.klass.name == KotlinType.LIST.qn &&
                parameter.type.typeParameters.single().klass.name == exactColumn.type.klass.name -> Condition(
                exactColumn.column.name,
                false,
                Op.IN,
                parameter.name,
            )

            else -> error("type mismatch parameter: $parameter, and column: $exactColumn")
        }
    }

    val conditions = parameters.map { toCondition(it) }

    fun <T> List<T>.indicesOf(predicate: (T) -> Boolean): Set<Int> {
        val result = mutableSetOf<Int>()
        forEachIndexed { i, value -> if (predicate(value)) result += i }
        return result
    }

    val inIndices = conditions.indicesOf { it.op == Op.IN }

    val whereClause = conditions
        .takeIf { it.isNotEmpty() }
        ?.let {
            """
                WHERE ${
            it.joinToString(" AND ") {
                when {
                    it.op == Op.EQ && !it.nullable -> "\"${it.columnName}\" = ?"
                    it.op == Op.EQ && it.nullable -> "\"${it.columnName}\" IS NOT DISTINCT FROM ?"
                    it.op == Op.IN -> "\"${it.columnName}\" = ANY (?)"
                    else -> error("")
                }
            }
            }
            """.trimIndent()
        }

    val inClauses = parameters.filterIndexed { i, _ -> i in inIndices }

    val whereParameters = parameters.filterIndexed { i, _ -> i !in inIndices }

    val whereColumnsByParameters = whereParameters.associateWith {
        columnsByFieldName[it.name]
            ?: error(
                "cannot find field '${it.name}', among: ${columnsByFieldName.keys}, in class: ${repoMappedKlass.klass.name}, " +
                    "function: ?",
                // TODO
            )
    }

    var positionInQuery = 0
    val queryParameters = whereColumnsByParameters.map { (param, c) ->
        positionInQuery++
        QueryParameter(
            path = param.name,
            kotlinType = param.type,
            positionInQuery = positionInQuery,
            setterName = getterSetterName(c),
            isJson = c.column.type == PostgresType.JSONB,
            isEnum = c.type.klass.isEnum,
            isINClause = false,
            convertToArray = false,
            postgresType = c.column.type,
        )
    }

    val inColumnsByParameters = inClauses.associateWith {
        columnsByFieldName[it.name]
            ?: error(
                "cannot find field '${it.name}', among: ${columnsByFieldName.keys}, in class: ${repoMappedKlass.klass.name}, " +
                    "function: ?",
                // TODO
            )
    }

    val inClauseQueryParameters = inColumnsByParameters.map { (param, c) ->
        QueryParameter(
            path = param.name,
            kotlinType = param.type,
            positionInQuery = parameters.indexOf(param) + 1,
            setterName = getterSetterName(c),
            isJson = c.column.type == PostgresType.JSONB,
            isEnum = c.type.klass.isEnum,
            isINClause = true,
            convertToArray = toCondition(param).op == Op.IN,
            postgresType = c.column.type,
        )
    }

    return whereClause to (queryParameters + inClauseQueryParameters)
}

private fun generateWhere2(
    parameters: List<FunctionParameter>,
    repoMappedKlass: TableMapping,
): Pair<String?, List<QueryParameter>> {
    val param = parameters.single()

    val whereColumns = if (repoMappedKlass.columns.any { it.column.isId }) {
        repoMappedKlass.columns.filter { it.column.isId || it.column.isVersion }
    } else {
        repoMappedKlass.columns
    }

    val conditions = whereColumns.map {
        Condition(
            it.column.name,
            it.column.nullable,
            Op.EQ,
        )
    }

    val whereClause = conditions
        .takeIf { it.isNotEmpty() }
        ?.let {
            """
                WHERE ${
            it.joinToString(" AND ") {
                when {
                    it.op == Op.EQ && !it.nullable -> "\"${it.columnName}\" = ?"
                    it.op == Op.EQ && it.nullable -> "\"${it.columnName}\" IS NOT DISTINCT FROM ?"
                    it.op == Op.IN -> "\"${it.columnName}\" = ANY (?)"
                    else -> error("")
                }
            }
            }
            """.trimIndent()
        }

    val queryParameters = whereColumns.mapIndexed { i, c ->
        QueryParameter(
            path = (listOf(param.name) + c.path).joinToString("."),
            kotlinType = c.type,
            positionInQuery = i + 1,
            setterName = getterSetterName(c),
            isJson = c.column.type == PostgresType.JSONB,
            isEnum = c.type.klass.isEnum,
            isINClause = false,
            convertToArray = conditions[i].op == Op.IN,
            postgresType = c.column.type,
        )
    }
    return whereClause to queryParameters
}

private fun generateWhere(
    parameters: List<FunctionParameter>,
    where: AAnnotation,
): Pair<String?, List<QueryParameter>> {
    // TODO check projection has same fields as mapped class
    val parametersByName = parameters.associateBy { it.name }

    validateParameters(where.parameters["value"]!!, parameters, parametersByName)

    var whereClause = "WHERE ${where.parameters["value"]!!}"

    val inClauseParamNames = inClauseRegex.findAll(whereClause).map { it.groupValues[1] }.toList()

    whereClause = inClauseParamNames.fold(whereClause) { clause, paramName ->
        clause.replace(
            ":$paramName",
            "%$paramName",
        )
    }

    val paramsOrdered = parameterPlaceholderRegex
        .findAll(whereClause)
        .map { it.value.substringAfter(":") }
        .map { parametersByName[it] ?: error("Parameter '$it' not found") }
        .toList()

    whereClause = whereClause.replace(parameterPlaceholderRegex, "?")

    val queryParameters = paramsOrdered.mapIndexed { i, parameter ->
        val convertToArray = parameter.type.klass.name == KotlinType.LIST.qn

        val postgresType =
            if (convertToArray) {
                kotlinTypeToPostgresTypeMapping[KotlinType.of(parameter.type.typeParameters.single().klass.name)]
            } else {
                kotlinTypeToPostgresTypeMapping[KotlinType.of(parameter.type.klass.name)]
            } ?: PostgresType.NONE

        QueryParameter(
            path = parameter.name,
            kotlinType = parameter.type,
            positionInQuery = i + 1,
            setterName = KotlinType.of(parameter.type.klass.name)?.jdbcSetterName
                ?: error("cannot map to KotlinType: ${parameter.type.klass.name}"),
            isJson = false,
            isEnum = parameter.type.klass.isEnum,
            isINClause = false,
            convertToArray = convertToArray,
            postgresType = postgresType,
        )
    }

    val inClauseQueryParameters = inClauseParamNames
        .map { parametersByName[it]!! }
        .map { parameter ->
            val convertToArray = parameter.type.klass.name == KotlinType.LIST.qn

            val PostgresType =
                if (convertToArray) {
                    kotlinTypeToPostgresTypeMapping[KotlinType.of(parameter.type.typeParameters.single().klass.name)]
                } else {
                    kotlinTypeToPostgresTypeMapping[KotlinType.of(parameter.type.klass.name)]
                } ?: PostgresType.NONE

            QueryParameter(
                path = parameter.name,
                kotlinType = parameter.type,
                positionInQuery = -1,
                setterName = KotlinType.of(parameter.type.klass.name)?.jdbcSetterName
                    ?: error("cannot map to KotlinType: ${parameter.type.klass.name}"),
                isJson = false,
                isEnum = parameter.type.klass.isEnum,
                isINClause = true,
                convertToArray = convertToArray,
                postgresType = PostgresType,
            )
        }
    return whereClause to queryParameters + inClauseQueryParameters
}

private fun validateParameters(
    whereClause: String,
    parameters: List<FunctionParameter>,
    parametersByName: Map<String, FunctionParameter>,
) {
    val paramsOrdered = parameterPlaceholderRegex
        .findAll(whereClause)
        .map { it.value.substringAfter(":") }
        .map { parametersByName[it] ?: error("Parameter '$it' not found") }
        .toList()

    (parameters - paramsOrdered)
        .takeIf { it.isNotEmpty() }
        ?.let { error("unused parameters: $it") }
}

data class Condition(
    val columnName: String,
    val nullable: Boolean,
    val op: Op,
    val parameterName: String? = null,
)

enum class Op { EQ, IN }

private fun KlassFunction.toSaveMethod(mappedKlass: TableMapping): QueryMethod {
    val versionColumnName = mappedKlass.columns.find { it.column.isVersion }?.column?.name

    val param = parameters.singleOrNull()
        ?: throw KotgresException("save method must have a single parameter (List or an Entity). $this")

    val insert = """
        INSERT INTO ${mappedKlass.fullTableName()}
        (${mappedKlass.columns.joinToString { "\"${it.column.name}\"" }})
        VALUES (${mappedKlass.columns.joinToString { if (it.column.isVersion) "? + 1" else "?" }})
    """.trimIndent()

    val onConflict = if (
        mappedKlass.columns.any { it.column.isId } &&
        annotationConfigs.none { it.name == OnConflictFail::class.qualifiedName }
    ) {
        """
        
        ON CONFLICT (${mappedKlass.columns.filter { it.column.isId }.joinToString { it.column.name }}) DO 
        UPDATE SET ${
        mappedKlass.columns.joinToString {
            """"${it.column.name}" = EXCLUDED."${it.column.name}""""
        }
        }
        ${versionColumnName?.let { "WHERE ${mappedKlass.name}.$it = EXCLUDED.$it - 1" } ?: ""}
        """.trimIndent()
    } else {
        ""
    }

    val query = insert + onConflict

    val queryType = if (KotlinType.of(param.type.klass.name) == KotlinType.LIST) {
        QueryMethodType.BATCH
    } else {
        QueryMethodType.SINGLE
    }

    val pathStart = when (queryType) {
        QueryMethodType.BATCH -> emptyList()
        QueryMethodType.SINGLE -> listOf(param.name)
    }

    val parameters = mappedKlass.columns.mapIndexed { i, it ->
        QueryParameter(
            positionInQuery = i + 1,
            kotlinType = it.type,
            setterName = getterSetterName(it),
            path = (pathStart + it.path).joinToString("."),
            isJson = it.column.type.value == PostgresType.JSONB.value,
            isEnum = it.type.klass.isEnum,
            isINClause = false,
            convertToArray = false,
            postgresType = it.column.type,
        )
    }

    return QueryMethod(
        name = name,
        query = query,
        queryMethodParameters = this.parameters.map { QueryMethodParameter(it.name, it.type) },
        queryParameters = parameters,
        returnType = Type(Klass(KotlinType.UNIT.qn)),
        returnsCollection = false,
        objectConstructor = null,
        trueReturnType = Type(Klass(KotlinType.UNIT.qn)),
        pagination = null,
        type = queryType,
        optimisticallyLocked = versionColumnName != null,
    )
}

private fun flattenToColumns(klass: Klass, path: List<String> = emptyList()): List<ColumnMapping> {
    return klass.fields.flatMap { field ->
        val columnAnnotation = field.annotations.singleOrNull { it.name == Column::class.qualifiedName }

        val colType: PostgresType? = extractPostgresType(columnAnnotation, field)

        when {
            colType == null && field.type.klass.fields.isEmpty() -> {
                error("Cannot define PostgresType for field: ${klass.name}.${(path + field.name).joinToString(".")} of type ${field.type}. Specify type implicitly in @Column")
            }

            colType != null -> {
                val colName = columnAnnotation?.parameters?.get("name")?.takeIf { it.isNotEmpty() }
                    ?: field.name.camelToSnakeCase()
                listOf(
                    ColumnMapping(
                        path = path + field.name,
                        column = ColumnDefinition(
                            name = colName,
                            nullable = field.type.nullability == Nullability.NULLABLE,
                            type = colType,
                            isId = field.annotations.any { it.name == Id::class.qualifiedName },
                            isVersion = field.annotations.any { it.name == Version::class.qualifiedName },
                        ),
                        type = field.type,
                    ),
                )
            }

            else -> flattenToColumns(field.type.klass, path = path + field.name)
        }
    }
}

private fun extractPostgresType(
    columnAnnotation: AAnnotation?,
    field: Field,
): PostgresType? {
    val type = columnAnnotation?.parameters?.get("type")?.let { PostgresType.of(it) } ?: PostgresType.NONE
    if (type != PostgresType.NONE) {
        return type
    }
    val type2 = KotlinType.of(field.type.klass.name)?.let { kotlinTypeToPostgresTypeMapping[it] }
    if (type2 != null) {
        return type2
    }
    if (field.type.klass.isEnum) {
        return PostgresType.TEXT
    }
    return null
}

fun paginationQueryParameters(pagination: Pagination, otherQueryParametersSize: Int): List<QueryParameter> {
    return listOf(
        QueryParameter(
            path = pagination.parameterName + ".pageSize",
            kotlinType = Type(Klass(KotlinType.INT.qn)),
            positionInQuery = otherQueryParametersSize + 1,
            setterName = KotlinType.INT.jdbcSetterName!!,
            isJson = false,
            isEnum = false,
            isINClause = false,
            convertToArray = false,
            postgresType = PostgresType.INTEGER,
        ),
        QueryParameter(
            path = pagination.parameterName + ".offset",
            kotlinType = Type(Klass(KotlinType.INT.qn)),
            positionInQuery = otherQueryParametersSize + 2,
            setterName = KotlinType.INT.jdbcSetterName,
            isJson = false,
            isEnum = false,
            isINClause = false,
            convertToArray = false,
            postgresType = PostgresType.INTEGER,
        ),
    )
}
