package kotgres.kapt.mapper

import kotgres.annotations.Column
import kotgres.annotations.First
import kotgres.annotations.Id
import kotgres.annotations.Limit
import kotgres.annotations.PostgresRepository
import kotgres.annotations.Query
import kotgres.annotations.Table
import kotgres.annotations.Where
import kotgres.aux.ColumnDefinition
import kotgres.aux.PostgresType
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
import kotgres.kapt.model.repository.QueryParameter
import kotgres.kapt.model.repository.Repo
import kotgres.kapt.parser.KotlinType
import kotgres.kapt.parser.toQualifiedName

// ex: `:firstName`
val parameterPlaceholderRegex = Regex(":\\w*")

fun validationErrors(klass: Klass): List<String> {
    val entityKlass = klass.superclassParameter?.klass!!

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
    val mapped = superclassParameter?.klass!!
    val mappedKlass = mapped.toTableMapping()

    val queryMethods = toQueryMethods(functions, mappedKlass)

    return Repo(
        superKlass = this,
        queryMethods = queryMethods,
        mappedKlass = mappedKlass,
        saveAllMethod = saveAllQuery(mappedKlass),
        findAllMethod = findAllQuery(mappedKlass),
        deleteAllMethod = deleteAllQuery(mappedKlass),
        belongsToDb = annotations.filterIsInstance<PostgresRepository>()
            .single()
            .belongsToDb
            .takeIf { it.isNotEmpty() }
            ?.toQualifiedName()
            ?: dbQualifiedName
    )
}

private fun toQueryMethods(functions: List<KlassFunction>, mappedKlass: TableMapping): List<QueryMethod> {

    val (withWhere, withoutWhere) = functions.partition { it.annotationConfigs.any { it is Where } }
    val (withQuery, other) = withoutWhere.partition { it.annotationConfigs.any { it is Query } }

    return other.map { it.toQueryMethod(mappedKlass) } +
            withWhere.map { it.toQueryMethodWhere(mappedKlass) } +
            withQuery.map { it.toCustomQueryMethod() }
}

private fun KlassFunction.toCustomQueryMethod(): QueryMethod {

    val paginationParameter = parameters.firstOrNull() { it.type.klass.name == pageableQualifiedName }
    val paged = paginationParameter != null
    //TODO check return type Page + parameter Pageable

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

    val trueReturnType = if (returnsCollection || paged) {
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
        paged -> "LIMIT ? OFFSET ?"
        annotationConfigs.filterIsInstance<First>().isNotEmpty() -> "LIMIT 1"
        else -> "LIMIT 2"
    }

    val paginationParam = if (paged)
        Pagination(parameterName = paginationParameter!!.name,)
    else
        null

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

    val paginationQueryParameters = if (paged) {
        listOf(
            QueryParameter(
                path = paginationParameter!!.name + ".pageSize",
                kotlinType = Type(Klass(KotlinType.INT.qn)),
                positionInQuery = queryParameters.size + 1,
                setterName = KotlinType.INT.jdbcSetterName!!,
                isJson = false,
                isEnum = false,
                convertToArray = false,
                postgresType = PostgresType.INTEGER
            ),
            QueryParameter(
                path = paginationParameter.name + ".offset",
                kotlinType = Type(Klass(KotlinType.INT.qn)),
                positionInQuery = queryParameters.size + 2,
                setterName = KotlinType.INT.jdbcSetterName,
                isJson = false,
                isEnum = false,
                convertToArray = false,
                postgresType = PostgresType.INTEGER
            )
        )
    } else {
        emptyList()
    }

    var q = query.replace(parameterPlaceholderRegex, "?")
    if(limitClause != null){
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
        pagination = paginationParam,
        queryMethodParameters = this.parameters.map { QueryMethodParameter(it.name, it.type) }
    )
}

private fun KlassFunction.toQueryMethodWhere(
    mappedKlass: TableMapping,
): QueryMethod {

    val paginationParameter = parameters.firstOrNull() { it.type.klass.name == pageableQualifiedName }
    val paged = paginationParameter != null
    //TODO check return type Page + parameter Pageable

    val parameters = parameters.filter { it.type.klass.name != pageableQualifiedName }

    val returnsCollection = returnType.klass.name == QualifiedName("kotlin.collections", "List")

    val trueReturnType = if (returnsCollection || paged) {
        returnType.typeParameters.single()
    } else {
        returnType
    }

    val returnKlassTableMapping = trueReturnType.klass.toTableMapping()

    //TODO check projection has same fields as mapped class

    val parametersByName = parameters.associateBy { it.name }

    val where = annotationConfigs.filterIsInstance<Where>().single()

    val paramsOrdered = parameterPlaceholderRegex
        .findAll(where.value)
        .map { it.value.substringAfter(":") }
        .map { parametersByName[it] ?: error("Parameter '$it' not found, function '$name'") }
        .toList()

    (parameters - paramsOrdered).takeIf { it.isNotEmpty() }?.let { error("unused parameters: $it, function '$name'") }

    val isDelete = name.startsWith("delete")

    val limitClause = when {
        isDelete -> null
        returnsCollection -> annotationConfigs.filterIsInstance<Limit>().singleOrNull()?.value?.let { "LIMIT $it" }
        paged -> "LIMIT ? OFFSET ?"
        annotationConfigs.filterIsInstance<First>().isNotEmpty() -> "LIMIT 1"
        else -> "LIMIT 2"
    }

    val selectOrDeleteClause = if (isDelete) {
        "DELETE"
    } else {
        "SELECT ${returnKlassTableMapping.columns.joinToString { "\"${it.column.name}\"" }} "
    }

    val fromClause = "FROM ${mappedKlass.fullTableName()}"
    val whereClause = "WHERE ${where.value.replace(parameterPlaceholderRegex, "?")}"

    val paginationParam = if (paged)
        Pagination(parameterName = paginationParameter!!.name,)
    else
        null

    val queryMethodParameters = this.parameters.map { QueryMethodParameter(it.name, it.type) }

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

    val paginationQueryParameters = if (paged) {
        listOf(
            QueryParameter(
                path = paginationParameter!!.name + ".pageSize",
                kotlinType = Type(Klass(KotlinType.INT.qn)),
                positionInQuery = queryParameters.size + 1,
                setterName = KotlinType.INT.jdbcSetterName!!,
                isJson = false,
                isEnum = false,
                convertToArray = false,
                postgresType = PostgresType.INTEGER
            ),
            QueryParameter(
                path = paginationParameter.name + ".offset",
                kotlinType = Type(Klass(KotlinType.INT.qn)),
                positionInQuery = queryParameters.size + 2,
                setterName = KotlinType.INT.jdbcSetterName,
                isJson = false,
                isEnum = false,
                convertToArray = false,
                postgresType = PostgresType.INTEGER
            )
        )
    } else {
        emptyList()
    }


    return QueryMethod(
        name = name,
        query = listOfNotNull(selectOrDeleteClause, fromClause, whereClause, limitClause).joinToString("\n"),
        returnType = returnType,
        trueReturnType = trueReturnType,
        returnsCollection = returnsCollection,
        queryParameters = queryParameters + paginationQueryParameters,
        objectConstructor = returnKlassTableMapping.objectConstructor,
        pagination = paginationParam,
        queryMethodParameters = queryMethodParameters
    )
}

val pageableQualifiedName = QualifiedName("kotgres.aux.page", "Pageable")
private fun KlassFunction.toQueryMethod(repoMappedKlass: TableMapping): QueryMethod {

    val paginationParameter = parameters.firstOrNull() { it.type.klass.name == pageableQualifiedName }
    val paged = paginationParameter != null
    //TODO check return type Page + parameter Pageable

    val parameters = parameters.filter { it.type.klass.name != pageableQualifiedName }

    val returnsCollection = returnType.klass.name == QualifiedName("kotlin.collections", "List")

    val trueReturnType = if (returnsCollection || paged) {
        returnType.typeParameters.single()
    } else {
        returnType
    }

    val returnKlassTableMapping = trueReturnType.klass.toTableMapping()

    val columnsByFieldName = repoMappedKlass.columns.associateBy { it.path.last() }

    val whereColumnsByParameters = parameters.associateWith {
        columnsByFieldName[it.name]
            ?: error(
                "cannot find field '${it.name}', among: ${columnsByFieldName.keys}, in class: ${trueReturnType.klass.name}, " +
                        "function: ${name}"
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

    if (whereColumnsByParameters.isEmpty()) error("Empty query parameters, function name: $name")

    val isDelete = name.startsWith("delete")

    val limitClause = when {
        isDelete -> null
        returnsCollection -> annotationConfigs.filterIsInstance<Limit>().singleOrNull()?.value?.let { "LIMIT $it" }
        paged -> "LIMIT ? OFFSET ?"
        annotationConfigs.filterIsInstance<First>().isNotEmpty() -> "LIMIT 1"
        else -> "LIMIT 2"
    }

    val selectOrDelete = if (isDelete) {
        """
        DELETE 
        """.trimIndent()
    } else {
        """
        SELECT ${returnKlassTableMapping.columns.joinToString { "\"${it.column.name}\"" }}
        """.trimIndent()
    }

    val from = "FROM ${repoMappedKlass.fullTableName()}"

    val whereClause = """
        WHERE ${
        conditions.joinToString(" AND ") {
            when {
                it.op == Op.EQ && !it.nullable -> "\"${it.columnName}\" = ?"
                it.op == Op.EQ && it.nullable -> "\"${it.columnName}\" IS NOT DISTINCT FROM ?"
                it.op == Op.IN -> "\"${it.columnName}\" = ANY (?)"
                else -> error("")
            }
        }
    }
    """.trimIndent()

    val paginationParameters = if (paged)
        Pagination(parameterName = paginationParameter!!.name)
    else
        null

    val queryMethodParameters = this.parameters.map { QueryMethodParameter(it.name, it.type) }

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

    val paginationQueryParameters = if (paged) {
        listOf(
            QueryParameter(
                path = paginationParameter!!.name + ".pageSize",
                kotlinType = Type(Klass(KotlinType.INT.qn)),
                positionInQuery = queryParameters.size + 1,
                setterName = KotlinType.INT.jdbcSetterName!!,
                isJson = false,
                isEnum = false,
                convertToArray = false,
                postgresType = PostgresType.INTEGER
            ),
            QueryParameter(
                path = paginationParameter.name + ".offset",
                kotlinType = Type(Klass(KotlinType.INT.qn)),
                positionInQuery = queryParameters.size + 2,
                setterName = KotlinType.INT.jdbcSetterName,
                isJson = false,
                isEnum = false,
                convertToArray = false,
                postgresType = PostgresType.INTEGER
            )
        )
    } else {
        emptyList()
    }

    return QueryMethod(
        name = name,
        query = listOfNotNull(selectOrDelete, from, whereClause, limitClause).joinToString("\n"),
        queryMethodParameters = queryMethodParameters,
        queryParameters = queryParameters + paginationQueryParameters,
        returnType = returnType,
        trueReturnType = trueReturnType,
        returnsCollection = returnsCollection,
        pagination = paginationParameters,
        objectConstructor = returnKlassTableMapping.objectConstructor
    )
}

data class Pagination(
    val parameterName: String,
)

data class Condition(
    val columnName: String,
    val nullable: Boolean,
    val op: Op,
)

enum class Op { EQ, IN }

private fun saveAllQuery(mappedKlass: TableMapping): QueryMethod {
    val insert = """
        INSERT INTO ${mappedKlass.fullTableName()}
        (${mappedKlass.columns.joinToString { "\"${it.column.name}\"" }})
        VALUES (${mappedKlass.columns.joinToString { "?" }})
    """.trimIndent()
    val onConflict = """
        
        ON CONFLICT (${mappedKlass.columns.filter { it.column.isId }.joinToString { it.column.name }}) DO 
        UPDATE SET ${
        mappedKlass.columns.joinToString { "\"${it.column.name}\" = EXCLUDED.\"${it.column.name}\"" }
    }
    """.trimIndent()

    val query = if (mappedKlass.columns.any { it.column.isId }) insert + onConflict else insert

    val parameters = mappedKlass.columns.mapIndexed { i, it ->
        QueryParameter(
            positionInQuery = i + 1,
            kotlinType = it.type,
            setterName = getterSetterName(it),
            path = it.path.joinToString("."),
            isJson = it.column.type == PostgresType.JSONB,
            isEnum = it.type.klass.isEnum,
            convertToArray = false,
            postgresType = it.column.type,
        )
    }
    return QueryMethod(
        name = "saveAll",
        query = query,
        queryMethodParameters = emptyList(),
        queryParameters = parameters,
        returnType = Type(Klass(KotlinType.UNIT.qn)),
        returnsCollection = false,
        objectConstructor = null,
        trueReturnType = Type(Klass(KotlinType.UNIT.qn)),
        pagination = null,
    )
}

private fun deleteAllQuery(mappedKlass: TableMapping): QueryMethod {
    val delete = "DELETE FROM ${mappedKlass.fullTableName()}".trimMargin()

    return QueryMethod(
        name = "saveAll",
        query = delete,
        queryParameters = emptyList(),
        returnType = Type(Klass(KotlinType.UNIT.qn)),
        trueReturnType = Type(Klass(KotlinType.UNIT.qn)),
        returnsCollection = false,
        objectConstructor = null,
        pagination = null,
        queryMethodParameters = emptyList(),
    )
}

private fun findAllQuery(mappedKlass: TableMapping): QueryMethod {
    val select = """
        SELECT ${mappedKlass.columns.joinToString { "\"${it.column.name}\"" }}
        FROM ${mappedKlass.fullTableName()}
    """.trimIndent()

    return QueryMethod(
        name = "findAll",
        query = select,
        queryParameters = emptyList(),
        returnType = Type(
            klass = Klass(name = QualifiedName("kotlin.collections", "List")),
            typeParameters = listOf(Type(mappedKlass.klass)),
        ),
        returnsCollection = true,
        objectConstructor = mappedKlass.objectConstructor,
        trueReturnType = Type(mappedKlass.klass),
        pagination = null,
        queryMethodParameters = emptyList(),
    )
}

private fun flattenToColumns(klass: Klass, path: List<String> = emptyList()): List<ColumnMapping> {
    return klass.fields.flatMap { field ->
        val columnAnnotation = field.annotations.filterIsInstance<Column>().singleOrNull()

        val colType: PostgresType? = extractPostrgresType(columnAnnotation, field)

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
                            isId = field.annotations.filterIsInstance<Id>().singleOrNull()?.let { true } ?: false
                        ),
                        type = field.type,
                    )
                )
            }
            else -> flattenToColumns(field.type.klass, path = path + field.name)
        }
    }
}

private fun TableMapping.fullTableName(): String = listOfNotNull(schema, name).joinToString(".") { "\"$it\"" }

private fun extractPostrgresType(
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
