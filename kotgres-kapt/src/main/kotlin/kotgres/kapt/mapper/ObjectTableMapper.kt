package kotgres.kapt.mapper

import kotgres.annotations.Column
import kotgres.annotations.First
import kotgres.annotations.Id
import kotgres.annotations.Limit
import kotgres.annotations.OnConflictFail
import kotgres.annotations.OrderBy
import kotgres.annotations.PostgresRepository
import kotgres.annotations.Query
import kotgres.annotations.Table
import kotgres.annotations.Version
import kotgres.annotations.Where
import kotgres.aux.ColumnDefinition
import kotgres.aux.PostgresType
import kotgres.kapt.KotgresException
import kotgres.kapt.model.db.ColumnMapping
import kotgres.kapt.model.db.TableMapping
import kotgres.kapt.model.klass.Field
import kotgres.kapt.model.klass.FunctionParameter
import kotgres.kapt.model.klass.Klass
import kotgres.kapt.model.klass.KlassFunction
import kotgres.kapt.model.klass.Nullability
import kotgres.kapt.model.klass.QualifiedName
import kotgres.kapt.model.klass.Type
import kotgres.kapt.model.klass.primitives
import kotgres.kapt.model.repository.ObjectConstructor
import kotgres.kapt.model.repository.QueryMethod
import kotgres.kapt.model.repository.QueryMethodParameter
import kotgres.kapt.model.repository.QueryMethodType
import kotgres.kapt.model.repository.QueryParameter
import kotgres.kapt.model.repository.Repo
import kotgres.kapt.parser.KotlinType
import kotgres.kapt.parser.toQualifiedName

// ex: `:firstName`
val parameterPlaceholderRegex = Regex(":\\w*")

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
    val tableAnnotation = annotations.filterIsInstance<Table>()
        .singleOrNull()

    val tableName = tableAnnotation
        ?.name
        ?.takeIf { it.isNotEmpty() }
        ?: name.name.camelToSnakeCase()

    val columns = flattenToColumns(this)

    val objectConstructor = if (name != KotlinType.UNIT.qn) objectConstructor(this, columns) else null

    return TableMapping(
        name = tableName,
        klass = this,
        columns = columns,
        objectConstructor = objectConstructor,
        schema = tableAnnotation?.schema,
    )
}

private fun objectConstructor(
    klass: Klass,
    columns: List<ColumnMapping>,
    parentField: String? = null,
    path: List<String> = emptyList()
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
                isNullable = column.type.nullability == Nullability.NULLABLE
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
                        path + it.name
                    )
                }
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
        belongsToDb = annotations.filterIsInstance<PostgresRepository>()
            .single()
            .belongsToDb
            .takeIf { it.isNotEmpty() }
            ?.toQualifiedName()
            ?: dbQualifiedName
    )
}

private fun toQueryMethods(functions: List<KlassFunction>, mappedKlass: TableMapping?): List<QueryMethod> {

    val (custom, dedicated) = functions.partition { it.annotationConfigs.any { it is Query } }
    val (saves, notSave) = dedicated.partition { it.name.startsWith("save") }
    val (deletes, queries) = notSave.partition { it.name.startsWith("delete") }


    if (dedicated.isNotEmpty() && mappedKlass == null)
        throw KotgresException("Only method with custom @Queries are allowed in standalone repositories")

    return queries.map { it.toQueryMethod(mappedKlass!!) } +
            custom.map { it.toCustomQueryMethod() } +
            saves.map { it.toSaveMethod(mappedKlass!!) } +
            deletes.map { it.toDeleteMethod(mappedKlass!!) }
}

private fun KlassFunction.toDeleteMethod(repoMappedKlass: TableMapping): QueryMethod {

    //TODO check no Limit no pageable

    val (whereClause, queryParameters) = when {
        annotationConfigs.any { it is Where } -> generateWhere(
            parameters,
            annotationConfigs.single { it is Where } as Where)
        parameters.size == 1 && parameters.first().type.klass == repoMappedKlass.klass -> generateWhere2(
            parameters,
            repoMappedKlass
        )
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
        optimisticallyLocked = false, //TODO
    )
}

private fun KlassFunction.toCustomQueryMethod(): QueryMethod {

    val paginationParameter = paginationParameter()

    val parameters = parameters.filter { it.type.klass.name != pageableQualifiedName }

    val query = annotationConfigs.filterIsInstance<Query>().singleOrNull()?.value!!

    val parametersByName = parameters.associateBy { it.name }

    val queryParametersOrdered = parameterPlaceholderRegex
        .findAll(query)
        .map { it.value.substringAfter(":") }
        .map { parametersByName[it] ?: error("Parameter '$it' not found, function '$name'") }
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
                columnName = "N/A",//TODO introduce another class?
                fieldName = null,
                fieldType = if (trueReturnType.klass.isEnum) trueReturnType.klass.name else kotlinType!!.qn,
                isJson = false,//TODO
                isEnum = trueReturnType.klass.isEnum,
                isPrimitive = trueReturnType.klass.name in primitives,
                isNullable = trueReturnType.nullability == Nullability.NULLABLE,
            )
        }

        else -> trueReturnType.klass.toTableMapping().objectConstructor
    }

    val limitClause = when {
        returnType.klass.name == KotlinType.UNIT.qn -> null
        returnsCollection -> annotationConfigs.filterIsInstance<Limit>().singleOrNull()?.value?.let { "LIMIT $it" }
        paginationParameter != null -> "LIMIT ? OFFSET ?"
        annotationConfigs.filterIsInstance<First>().isNotEmpty() -> "LIMIT 1"
        else -> "LIMIT 2"
    }

    val queryParameters = queryParametersOrdered.mapIndexed { i, it ->
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
            convertToArray = convertToArray,
            postgresType = postgresType,
        )
    }

    val paginationQueryParameters = paginationParameter
        ?.let { paginationQueryParameters(it, queryParameters.size) }
        ?: emptyList()

    var q = query.replace(parameterPlaceholderRegex, "?")
    if (limitClause != null) {
        q = q + "\n" + limitClause
    }

    return QueryMethod(
        name = name,
        query = q,
        returnType = returnType,
        trueReturnType = trueReturnType,
        returnsCollection = returnsCollection,
        queryParameters = queryParameters + paginationQueryParameters,
        objectConstructor = constructor,
        returnsScalar = isScalar,
        pagination = paginationParameter,
        queryMethodParameters = this.parameters.map { QueryMethodParameter(it.name, it.type) },
        optimisticallyLocked = false,
    )
}

private fun KlassFunction.toQueryMethod(repoMappedKlass: TableMapping): QueryMethod {

    val paginationParameter = paginationParameter()

    val orderQualifiedName = QualifiedName("kotgres.aux.sort", "Order")
    val orderParam = parameters.singleOrNull { it.type.klass.name == orderQualifiedName }

    val limitParameter = parameters.singleOrNull { it.annotations.any { it is Limit } }

    val parameters = parameters.filter {
        it.type.klass.name != pageableQualifiedName &&
                it.type.klass.name != orderQualifiedName &&
                it.annotations.none { it is Limit }
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
        .singleOrNull { it is Where }
        ?.let { generateWhere(parameters, it as Where) }
        ?: generateWhere(parameters, repoMappedKlass)

    val methodAnnotation = annotationConfigs.filterIsInstance<Limit>().singleOrNull()

    val limitClause = when {
        isCount || isExists -> null
        returnsCollection && limitParameter != null -> "LIMIT ?"
        returnsCollection && methodAnnotation != null -> "LIMIT ${methodAnnotation.value}"
        returnsCollection -> null
        paginationParameter != null -> "LIMIT ? OFFSET ?"
        annotationConfigs.filterIsInstance<First>().isNotEmpty() -> "LIMIT 1"
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

    val orderByAnnotation = annotationConfigs.singleOrNull { it is OrderBy }

    val orderClause = when {
        orderParam != null -> "%orderBy"
        orderByAnnotation != null -> "ORDER BY " + (orderByAnnotation as OrderBy).value
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
                    convertToArray = false,
                    PostgresType.INTEGER
                )
            )
            else -> emptyList()
        }

    val constructor = when {
        isCount || isExists -> ObjectConstructor.Extractor(
            resultSetGetterName = KotlinType.of(trueReturnType.klass.name)!!.jdbcSetterName!!,
            columnName = "N/A",
            fieldName = null,
            fieldType = trueReturnType.klass.name,
            isJson = false,
            isEnum = false,
            isPrimitive = true,
            isNullable = trueReturnType.nullability == Nullability.NULLABLE,
        )
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

    val whereColumnsByParameters = parameters.associateWith {
        columnsByFieldName[it.name]
            ?: error(
                ""//TODO
//                "cannot find field '${it.name}', among: ${columnsByFieldName.keys}, in class: ${repoMappedKlass.klass.name}, " +
//                        "function: ${name}"
            )
    }

    //todo check parameter types with column types

    fun toCondition(parameter: FunctionParameter): Condition {
        val exactColumn = columnsByFieldName[parameter.name]
        if (exactColumn != null && exactColumn.type.klass == parameter.type.klass) {
            return Condition(
                exactColumn.column.name,
                nullable = exactColumn.type.nullability == Nullability.NULLABLE && parameter.type.nullability == Nullability.NULLABLE,
                Op.EQ,
            )
        }
        if (exactColumn != null &&
            parameter.type.klass.name == KotlinType.LIST.qn &&
            parameter.type.typeParameters.single().klass.name == exactColumn.type.klass.name
        ) {
            return Condition(exactColumn.column.name, false, Op.IN)
        }
        error("type mismatch") //TODO more useful message
    }

    val conditions = parameters.map { toCondition(it) }

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

    val queryParameters = whereColumnsByParameters.values.mapIndexed { i, c ->
        QueryParameter(
            path = parameters[i].name,
            kotlinType = parameters[i].type,
            positionInQuery = i + 1,
            setterName = getterSetterName(c),
            isJson = c.column.type == PostgresType.JSONB,
            isEnum = c.type.klass.isEnum,
            convertToArray = conditions[i].op == Op.IN,
            postgresType = c.column.type,
        )
    }
    return whereClause to queryParameters
}


private fun generateWhere2(
    parameters: List<FunctionParameter>,
    repoMappedKlass: TableMapping,
): Pair<String?, List<QueryParameter>> {

    val param = parameters.single()

    val whereColumns = if (repoMappedKlass.columns.any { it.column.isId }) {
        repoMappedKlass.columns.filter { it.column.isId }
    } else {
        repoMappedKlass.columns
    }

    val conditions = whereColumns.map { Condition(it.column.name, it.column.nullable, Op.EQ) }

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
            convertToArray = conditions[i].op == Op.IN,
            postgresType = c.column.type,
        )
    }
    return whereClause to queryParameters
}


private fun generateWhere(
    parameters: List<FunctionParameter>,
    where: Where
): Pair<String?, List<QueryParameter>> {
    //TODO check projection has same fields as mapped class

    val parametersByName = parameters.associateBy { it.name }

    val paramsOrdered = parameterPlaceholderRegex
        .findAll(where.value)
        .map { it.value.substringAfter(":") }
        .map {
            parametersByName[it] ?: error(
                ""//TODO
                //"Parameter '$it' not found, function '$name'"
            )
        }
        .toList()

    (parameters - paramsOrdered).takeIf { it.isNotEmpty() }?.let {
        error(
            ""//TODO
            //    "unused parameters: $it, function '$name'"
        )
    }

    val whereClause = "WHERE ${where.value.replace(parameterPlaceholderRegex, "?")}"

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
            convertToArray = convertToArray,
            postgresType = postgresType,
        )
    }
    return whereClause to queryParameters
}

data class Condition(
    val columnName: String,
    val nullable: Boolean,
    val op: Op,
)

enum class Op { EQ, IN }

private fun KlassFunction.toSaveMethod(mappedKlass: TableMapping): QueryMethod {

    val versionColumnName = mappedKlass.columns.find { it.column.isVersion }?.column?.name

    val param = parameters.singleOrNull()
        ?: throw KotgresException("save method must have a single parameter (List or an Entity). $this")

    val insert = """
        INSERT INTO ${mappedKlass.fullTableName()}
        (${mappedKlass.columns.joinToString { "\"${it.column.name}\"" }})
        VALUES (${mappedKlass.columns.joinToString { if(it.column.isVersion)"? + 1" else "?" }})
        """.trimIndent()

    val onConflict = if (
        mappedKlass.columns.any { it.column.isId } &&
        annotationConfigs.none { it is OnConflictFail }
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
            isJson = it.column.type == PostgresType.JSONB,
            isEnum = it.type.klass.isEnum,
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
        val columnAnnotation = field.annotations.filterIsInstance<Column>().singleOrNull()

        val colType: PostgresType? = extractPostgresType(columnAnnotation, field)

        when {
            colType == null && field.type.klass.fields.isEmpty() -> {
                error("Cannot define PostgresType for field: ${klass.name}.${(path + field.name).joinToString(".")} of type ${field.type}. Specify type implicitly in @Column")
            }
            colType != null -> {
                val colName = columnAnnotation?.name?.takeIf { it.isNotEmpty() } ?: field.name.camelToSnakeCase()
                listOf(
                    ColumnMapping(
                        path = path + field.name,
                        column = ColumnDefinition(
                            name = colName,
                            nullable = field.type.nullability == Nullability.NULLABLE,
                            type = colType,
                            isId = field.annotations.any { it is Id },
                            isVersion = field.annotations.any { it is Version },
                        ),
                        type = field.type,
                    )
                )
            }
            else -> flattenToColumns(field.type.klass, path = path + field.name)
        }
    }
}

private fun TableMapping.fullTableName(): String =
    listOfNotNull(schema, name).joinToString(".") { "\"$it\"" }

private fun extractPostgresType(
    columnAnnotation: Column?,
    field: Field
): PostgresType? {
    val type = columnAnnotation?.type ?: PostgresType.NONE
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
