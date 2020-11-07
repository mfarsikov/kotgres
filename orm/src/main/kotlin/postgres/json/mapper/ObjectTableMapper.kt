package postgres.json.mapper

import postgres.json.lib.Column
import postgres.json.lib.Id
import postgres.json.lib.Table
import postgres.json.lib.Where
import postgres.json.model.db.ColumnDefinition
import postgres.json.model.db.ColumnMapping
import postgres.json.model.db.PostgresType
import postgres.json.model.db.TableMapping
import postgres.json.model.klass.Klass
import postgres.json.model.klass.KlassFunction
import postgres.json.model.klass.Nullability
import postgres.json.model.klass.QualifiedName
import postgres.json.model.klass.Type
import postgres.json.model.repository.ObjectConstructor
import postgres.json.model.repository.QueryMethod
import postgres.json.model.repository.QueryParameter
import postgres.json.model.repository.Repo
import postgres.json.parser.KotlinType

// ex: `:firstName`
val parameterPlaceholderRegex = Regex(":\\w*")

fun Klass.toTableMapping(): TableMapping {
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
        ObjectConstructor.Extractor(
            resultSetGetterName = KotlinType.of(column.type.klass.name)?.jdbcSetterName
                ?: error("cannot map to KotlinType: ${column.type.klass.name}"),
            columnName = column.column.name,
            fieldName = parentField,
            fieldType = column.type.klass.name,
            isJson = column.column.type == PostgresType.JSONB
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

fun Klass.toRepo(): Repo {
    val mapped = superclassParameter?.klass!!
    val mappedKlass = mapped.toTableMapping()

    val (withWhere, withoutWhere) = functions.partition { it.annotationConfigs.any { it is Where } }

    return Repo(
        superKlass = this,
        queryMethods = withoutWhere.map { it.toQueryMethod(mappedKlass) } + withWhere.map {
            it.toQueryMethodWhere(
                mappedKlass
            )
        },
        mappedKlass = mappedKlass,
        saveAllMethod = saveAllQuery(mappedKlass),
        findAllMethod = findAllQuery(mappedKlass),
        deleteAllMethod = deleteAllQuery(mappedKlass)
    )
}


fun KlassFunction.toQueryMethodWhere(mappedKlass: TableMapping): QueryMethod {

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

    return QueryMethod(
        name = name,
        query = listOf(selectOrDeleteClause, fromClause, whereClause).joinToString("\n"),
        returnType = returnType,
        returnsCollection = returnType.klass.name == QualifiedName("kotlin.collections", "List"),
        queryParameters = paramsOrdered.mapIndexed { i, it ->
            QueryParameter(
                path = it.name,
                type = it.type,
                position = i + 1,
                setterType = KotlinType.of(it.type.klass.name)?.jdbcSetterName
                    ?: error("cannot map to KotlinType: ${it.type.klass.name}"),
                isJson = false
            )
        },
    )
}

fun KlassFunction.toQueryMethod(mappedKlass: TableMapping): QueryMethod {

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

    return QueryMethod(
        name = name,
        query = listOf(selectOrDelete, from, whereClause).joinToString("\n"),
        queryParameters = whereColumnsByParameters.values.mapIndexed { i, c ->
            QueryParameter(
                path = parameters[i].name,
                type = c.type,
                position = i + 1,
                setterType = KotlinType.of(c.type.klass.name)?.jdbcSetterName
                    ?: error("cannot map to KotlinType: ${c.type.klass.name}"),
                isJson = c.column.type == PostgresType.JSONB
            )
        },
        returnType = returnType,
        returnsCollection = returnType.klass.name == QualifiedName("kotlin.collections", "List"),
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
            setterType = KotlinType.of(it.type.klass.name)?.jdbcSetterName
                ?: error("cannot map to KotlinType: ${it.type.klass.name}"),
            path = it.path.joinToString("."),
            isJson = it.column.type == PostgresType.JSONB
        )
    }
    return QueryMethod(
        name = "saveAll",
        query = query,
        queryParameters = parameters,
        returnType = Type(Klass(KotlinType.UNIT.qn)),
        returnsCollection = false,
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
    )
}

private fun findAllQuery(mappedKlass: TableMapping): QueryMethod {
    //TODO injections (sql and kotlin)
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
        returnsCollection = true
    )
}

private fun flattenToColumns(klass: Klass, path: List<String> = emptyList()): List<ColumnMapping> {
    return klass.fields.flatMap { field ->
        val columnAnnotation = field.annotations.filterIsInstance<Column>().singleOrNull()

        val colType: PostgresType? = columnAnnotation?.type.takeIf { it != PostgresType.NONE }
        ?: KotlinType.of(field.type.klass.name)?.let{kotlinTypeToPostgresTypeMapping[it]}

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

val kotlinTypeToPostgresTypeMapping = mapOf(
    KotlinType.BIG_DECIMAL to PostgresType.NUMERIC,
    KotlinType.BOOLEAN to PostgresType.BOOLEAN,
    KotlinType.BYTE_ARRAY to PostgresType.BYTEA,
    KotlinType.DATE to PostgresType.DATE,
    KotlinType.DOUBLE to PostgresType.DOUBLE,
    KotlinType.FLOAT to PostgresType.REAL,
    KotlinType.INSTANT to PostgresType.TIMESTAMP_WITH_TIMEZONE,
    KotlinType.INT to PostgresType.INTEGER,
    KotlinType.LIST to PostgresType.JSONB,
    KotlinType.LONG to PostgresType.BIGINT,
    KotlinType.LOCAL_DATE to PostgresType.DATE,
    KotlinType.LOCAL_DATE_TIME to PostgresType.TIMESTAMP,
    KotlinType.LOCAL_TIME to PostgresType.TIME,
    KotlinType.MAP to PostgresType.JSONB,
    KotlinType.MUTABLE_LIST to PostgresType.JSONB,
    KotlinType.MUTABLE_MAP to PostgresType.JSONB,
    KotlinType.STRING to PostgresType.TEXT,
    KotlinType.TIME to PostgresType.TIME,
    KotlinType.TIMESTAMP to PostgresType.TIMESTAMP_WITH_TIMEZONE,
    KotlinType.UUID to PostgresType.UUID,
)