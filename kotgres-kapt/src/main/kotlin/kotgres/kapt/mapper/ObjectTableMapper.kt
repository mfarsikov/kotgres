package kotgres.kapt.mapper

import kotgres.annotations.Column
import kotgres.annotations.Id
import kotgres.annotations.Query
import kotgres.annotations.Table
import kotgres.annotations.Where
import kotgres.aux.ColumnDefinition
import kotgres.aux.PostgresType
import kotgres.kapt.model.db.ColumnMapping
import kotgres.kapt.model.db.TableMapping
import kotgres.kapt.model.klass.Field
import kotgres.kapt.model.klass.Klass
import kotgres.kapt.model.klass.KlassFunction
import kotgres.kapt.model.klass.Nullability
import kotgres.kapt.model.klass.QualifiedName
import kotgres.kapt.model.klass.Type
import kotgres.kapt.model.repository.ObjectConstructor
import kotgres.kapt.model.repository.QueryMethod
import kotgres.kapt.model.repository.QueryParameter
import kotgres.kapt.model.repository.Repo
import kotgres.kapt.parser.KotlinType

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
    val tableName = annotations.filterIsInstance<Table>()
        .singleOrNull()
        ?.name
        ?.takeIf { it.isNotEmpty() }
        ?: name.name.camelToSnakeCase()

    val columns = flattenToColumns(this)

    return TableMapping(
        name = tableName,
        klass = this,
        columns = columns,
        objectConstructor = objectConstructor(this, columns)
    )
}

private fun objectConstructor(
    klass: Klass,
    columns: List<ColumnMapping>,
    parentField: String? = null,
    path: List<String> = emptyList()
): ObjectConstructor {
    return if (klass.fields.isEmpty()) {
        val column = columns.single { it.path == path }

        column.type.klass.isEnum
        ObjectConstructor.Extractor(
            resultSetGetterName = getterSetterName(column),
            columnName = column.column.name,
            fieldName = parentField,
            fieldType = column.type.klass.name,
            isJson = column.column.type == PostgresType.JSONB,
            isEnum = column.type.klass.isEnum,
        )
    } else {
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

fun getterSetterName(column: ColumnMapping): String {
    return if (column.type.klass.isEnum) "String" else KotlinType.of(column.type.klass.name)?.jdbcSetterName
        ?: error("cannot map to KotlinType: ${column.type.klass.name}")
}

fun Klass.toRepo(): Repo {
    val mapped = superclassParameter?.klass!!
    val mappedKlass = mapped.toTableMapping()

    val queryMethods = toQueryMethods(functions, mappedKlass)
    return Repo(
        superKlass = this,
        queryMethods = queryMethods,
        mappedKlass = mappedKlass,
        saveAllMethod = saveAllQuery(mappedKlass),
        findAllMethod = findAllQuery(mappedKlass),
        deleteAllMethod = deleteAllQuery(mappedKlass)
    )
}

private fun toQueryMethods(functions: List<KlassFunction>, mappedKlass: TableMapping): List<QueryMethod> {

    val (withWhere, withoutWhere) = functions.partition { it.annotationConfigs.any { it is Where } }
    val (withQuery, other) = withoutWhere.partition { it.annotationConfigs.any { it is Query } }


    val queryMethods = other.map { it.toQueryMethod(mappedKlass, mappedKlass.objectConstructor) } +
            withWhere.map { it.toQueryMethodWhere(mappedKlass, mappedKlass.objectConstructor) } +
            withQuery.map { it.toCustomQueryMethod() }
    return queryMethods
}

private fun KlassFunction.toCustomQueryMethod(): QueryMethod {
    val projectionMapping = returnType.klass.toTableMapping()

    val query = annotationConfigs.filterIsInstance<Query>().singleOrNull()?.value!!

    val parametersByName = parameters.associateBy { it.name }

    val paramsOrdered = parameterPlaceholderRegex
        .findAll(query)
        .map { it.value.substringAfter(":") }
        .map { parametersByName[it] ?: error("Parameter '$it' not found, function '$name'") }
        .toList()

    (parameters - paramsOrdered).takeIf { it.isNotEmpty() }?.let { error("unused parameters: $it, function '$name'") }

    val returnsCollection = returnType.klass.name == QualifiedName("kotlin.collections", "List")
    val returnKlass = if (returnsCollection) {
        returnType.typeParameters.single().klass
    } else {
        returnType.klass
    }


    return QueryMethod(
        name = name,
        query = query.replace(parameterPlaceholderRegex, "?"),
        returnType = returnType,
        returnKlass = returnKlass,
        returnsCollection = returnsCollection,
        queryParameters = paramsOrdered.mapIndexed { i, it ->
            QueryParameter(
                path = it.name,
                type = it.type,
                position = i + 1,
                setterType = KotlinType.of(it.type.klass.name)?.jdbcSetterName
                    ?: error("cannot map to KotlinType: ${it.type.klass.name}"),
                isJson = false,
                isEnum = it.type.klass.isEnum,
            )
        },
        objectConstructor = objectConstructor(returnType.klass, projectionMapping.columns)
    )
}

private fun KlassFunction.toQueryMethodWhere(
    mappedKlass: TableMapping,
    objectConstructor: ObjectConstructor
): QueryMethod {

    val parametersByName = parameters.associateBy { it.name }

    val where = annotationConfigs.filterIsInstance<Where>().single()

    val paramsOrdered = parameterPlaceholderRegex
        .findAll(where.value)
        .map { it.value.substringAfter(":") }
        .map { parametersByName[it] ?: error("Parameter '$it' not found, function '$name'") }
        .toList()

    (parameters - paramsOrdered).takeIf { it.isNotEmpty() }?.let { error("unused parameters: $it, function '$name'") }

    val selectOrDeleteClause = if (name.startsWith("delete")) {
        "DELETE"
    } else {
        "SELECT ${mappedKlass.columns.joinToString { "\"${it.column.name}\"" }} "
    }

    val fromClause = "FROM \"${mappedKlass.name}\" "
    val whereClause = "WHERE ${where.value.replace(parameterPlaceholderRegex, "?")}"

    val returnsCollection = returnType.klass.name == QualifiedName("kotlin.collections", "List")
    val returnKlass = if (returnsCollection) {
        returnType.typeParameters.single().klass
    } else {
        returnType.klass
    }

    return QueryMethod(
        name = name,
        query = listOf(selectOrDeleteClause, fromClause, whereClause).joinToString("\n"),
        returnType = returnType,
        returnKlass = returnKlass,
        returnsCollection = returnsCollection,
        queryParameters = paramsOrdered.mapIndexed { i, it ->
            QueryParameter(
                path = it.name,
                type = it.type,
                position = i + 1,
                setterType = KotlinType.of(it.type.klass.name)?.jdbcSetterName
                    ?: error("cannot map to KotlinType: ${it.type.klass.name}"),
                isJson = false,
                isEnum = it.type.klass.isEnum,
            )
        },
        objectConstructor = objectConstructor
    )
}

private fun KlassFunction.toQueryMethod(mappedKlass: TableMapping, objectConstructor: ObjectConstructor): QueryMethod {

    val columnsByFieldName = mappedKlass.columns.associateBy { it.path.last() }

    val whereColumnsByParameters = parameters.associateWith {
        columnsByFieldName[it.name]
            ?: error("cannot find field '${it.name}', among: ${columnsByFieldName.keys}")
    }

    if (whereColumnsByParameters.isEmpty()) error("Empty query parameters, function name: $name")

    val selectOrDelete = if (name.startsWith("delete")) {
        """
        DELETE 
        """.trimIndent()
    } else {
        """
        SELECT ${mappedKlass.columns.joinToString { "\"${it.column.name}\"" }}
        """.trimIndent()
    }

    val from = """FROM "${mappedKlass.name}""""

    val whereClause = """
        WHERE ${whereColumnsByParameters.values.joinToString(" AND ") { "\"${it.column.name}\" = ?" }}
    """.trimIndent()
    val returnsCollection = returnType.klass.name == QualifiedName("kotlin.collections", "List")

    val returnKlass = if(returnsCollection){returnType.typeParameters.single().klass}else{returnType.klass}


    return QueryMethod(
        name = name,
        query = listOf(selectOrDelete, from, whereClause).joinToString("\n"),
        queryParameters = whereColumnsByParameters.values.mapIndexed { i, c ->
            QueryParameter(
                path = parameters[i].name,
                type = c.type,
                position = i + 1,
                setterType = getterSetterName(c),
                isJson = c.column.type == PostgresType.JSONB,
                isEnum = c.type.klass.isEnum,
            )
        },
        returnType = returnType,
        returnKlass = returnKlass,
        returnsCollection = returnsCollection,
        objectConstructor = objectConstructor
    )
}

private fun saveAllQuery(mappedKlass: TableMapping): QueryMethod {
    //TODO injections (sql and kotlin)
    val insert = """
        INSERT INTO "${mappedKlass.name}" 
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
            position = i + 1,
            type = it.type,
            setterType = getterSetterName(it),
            path = it.path.joinToString("."),
            isJson = it.column.type == PostgresType.JSONB,
            isEnum = it.type.klass.isEnum,
        )
    }
    return QueryMethod(
        name = "saveAll",
        query = query,
        queryParameters = parameters,
        returnType = Type(Klass(KotlinType.UNIT.qn)),
        returnsCollection = false,
        objectConstructor = null,
        returnKlass = Klass(KotlinType.UNIT.qn),
    )
}

private fun deleteAllQuery(mappedKlass: TableMapping): QueryMethod {
    val delete = """DELETE FROM "${mappedKlass.name}" """.trimMargin()

    return QueryMethod(
        name = "saveAll",
        query = delete,
        queryParameters = emptyList(),
        returnType = Type(Klass(KotlinType.UNIT.qn)),
        returnsCollection = false,
        objectConstructor = null,
        returnKlass = Klass(KotlinType.UNIT.qn)
    )
}

private fun findAllQuery(mappedKlass: TableMapping): QueryMethod {
    val select = """
        SELECT ${mappedKlass.columns.joinToString { "\"${it.column.name}\"" }}
        FROM "${mappedKlass.name}" 
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
        returnKlass = mappedKlass.klass,
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

val kotlinTypeToPostgresTypeMapping = mapOf(
    KotlinType.BIG_DECIMAL to PostgresType.NUMERIC,
    KotlinType.BOOLEAN to PostgresType.BOOLEAN,
    KotlinType.BYTE_ARRAY to PostgresType.BYTEA,
    KotlinType.DATE to PostgresType.DATE,
    KotlinType.DOUBLE to PostgresType.DOUBLE,
    KotlinType.FLOAT to PostgresType.REAL,
    KotlinType.INT to PostgresType.INTEGER,
    KotlinType.LIST to PostgresType.JSONB,
    KotlinType.LONG to PostgresType.BIGINT,
    KotlinType.LOCAL_DATE to PostgresType.DATE,
    KotlinType.LOCAL_DATE_TIME to PostgresType.TIMESTAMP,
    KotlinType.LOCAL_TIME to PostgresType.TIME,
    KotlinType.MAP to PostgresType.JSONB,
    KotlinType.STRING to PostgresType.TEXT,
    KotlinType.TIME to PostgresType.TIME,
    KotlinType.TIMESTAMP to PostgresType.TIMESTAMP_WITH_TIMEZONE,
    KotlinType.UUID to PostgresType.UUID,
)

fun main() {
    kotlinTypeToPostgresTypeMapping.forEach { a, b ->
        println(a.qn.toString() + " | " + b.value)
    }
}