package postgres.json.mapper

import postgres.json.Logger
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
import postgres.json.model.repository.QueryMethod2
import postgres.json.model.repository.QueryParameter
import postgres.json.model.repository.QueryParameter2
import postgres.json.model.repository.Repo

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
            resultSetGetterName = column.column.type.toMethodName(),
            columnName = column.column.name,
            fieldName = parentField,
            converter = null
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

fun Klass.toRepo(mapped: Klass): Repo {
    val mappedKlass = mapped.toTableMapping()

    val (where, nowhere) = functions.partition { it.annotationConfigs.any { it is Where } }

    return Repo(
        superKlass = this,
        queryMethods = nowhere.map { it.toQueryMethod(mappedKlass) } + where.map { it.toQueryMethodWhere(mappedKlass) },
        mappedKlass = mappedKlass,
        saveAllMethod = saveAllQuery(mappedKlass),
        findAllMethod = findAllQuery(mappedKlass),
        deleteAllMethod = deleteAllQuery(mappedKlass)
    )
}


fun KlassFunction.toQueryMethodWhere(mappedKlass: TableMapping): QueryMethod2 {

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

    return QueryMethod2(
        name = name,
        query = listOf(selectOrDeleteClause, fromClause, whereClause).joinToString("\n"),
        queryParameters = paramsOrdered.mapIndexed { i, it ->
            val postgresType = typeMappings[it.type.klass.name]
                ?: error("cannot map to postgres type: ${it.type.klass.name}") //TODO add converter
            QueryParameter2(
                name = it.name,
                type = it.type,
                position = i + 1,
                postgresType = postgresType,
                setterType = postgresType.toMethodName(),
                converter = null
            )
        },
        returnType = returnType,
        returnsCollection = returnType.klass.name == QualifiedName("kotlin.collections", "List"),
    )
}

fun KlassFunction.toQueryMethod(mappedKlass: TableMapping): QueryMethod2 {

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

    return QueryMethod2(
        name = name,
        query = listOf(selectOrDelete, from, whereClause).joinToString("\n"),
        queryParameters = whereColumnsByParameters.values.mapIndexed { i, c ->
            QueryParameter2(
                name = parameters[i].name,
                type = parameters[i].type,
                position = i + 1,
                postgresType = c.column.type,
                setterType = c.column.type.toMethodName(),
                converter = null,
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
            type = it.column.type,
            converter = null,
            setterType = it.column.type.toMethodName(),
            path = it.path,
        )
    }
    return QueryMethod(
        name = "saveAll",
        query = query,
        queryParameters = parameters,
        returnType = null,
    )
}

private fun deleteAllQuery(mappedKlass: TableMapping): QueryMethod {
    val delete = """DELETE FROM "${mappedKlass.name}" """.trimMargin()

    return QueryMethod(
        name = "saveAll",
        query = delete,
        queryParameters = emptyList(),
        returnType = null,
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
    )
}

private fun PostgresType.toMethodName(): String = when (this) {
    PostgresType.TEXT -> "String"
    PostgresType.INTEGER -> "Int"
    PostgresType.JSONB -> "String"
    PostgresType.DATE -> "Date"
    //TODO
    else -> error("Unexpected postgres type: $this")
}

private fun flattenToColumns(klass: Klass, path: List<String> = emptyList()): List<ColumnMapping> {
    return klass.fields.flatMap { field ->
        val columnAnnotation = field.annotations.filterIsInstance<Column>().singleOrNull()

        val colType: PostgresType? = columnAnnotation
            ?.type.takeIf { it != PostgresType.NONE }
            ?: typeMappings[field.type.klass.name]

        when {
            colType == null && field.type.klass.fields.isEmpty() -> {
                Logger.error("Cannot define type for field: ${klass.name}.${path.joinToString(".")} of type ${field.type}. Specify type implicitly in @Column")
                emptyList()
            }
            colType != null -> {
                val colName = columnAnnotation?.name?.takeIf { it.isNotEmpty() } ?: field.name.camelToSnakeCase()
                listOf(
                    ColumnMapping(
                        path = path + field.name,
                        column = ColumnDefinition(
                            colName,
                            field.type.nullability == Nullability.NULLABLE,
                            colType,
                            isId = field.annotations.filterIsInstance<Id>().singleOrNull()?.let { true } ?: false
                        ),
                        type = field.type
                    )
                )
            }
            else -> flattenToColumns(field.type.klass, path = path + field.name)
        }
    }
}

private val typeMappings = mapOf(
    QualifiedName(pkg = "kotlin", name = "String") to PostgresType.TEXT,
    QualifiedName(pkg = "kotlin", name = "Long") to PostgresType.BIGINT,
    QualifiedName(pkg = "kotlin", name = "Int") to PostgresType.INTEGER,
    QualifiedName(pkg = "kotlin", name = "Double") to PostgresType.DOUBLE,
    QualifiedName(pkg = "kotlin", name = "Float") to PostgresType.REAL,
    QualifiedName(pkg = "kotlin", name = "Boolean") to PostgresType.BOOLEAN,
    QualifiedName(pkg = "java.util", name = "UUID") to PostgresType.UUID,
    QualifiedName(pkg = "java.time", name = "Instant") to PostgresType.UUID,
    QualifiedName(pkg = "java.time", name = "LocalDate") to PostgresType.DATE,
    QualifiedName(pkg = "java.time", name = "LocalTime") to PostgresType.TIME,
    //QualifiedName(pkg = "java.time", name = "LocalDateTime") to "timestamp", TODO use date + time?
    QualifiedName(pkg = "java.time", name = "ZonedDateTime") to PostgresType.TIMESTAMP_WITH_TIMEZONE,
    //QualifiedName(pkg = "java.time", name = "OffsetDateTime") to "timestamp with time zone",TODO?
)
