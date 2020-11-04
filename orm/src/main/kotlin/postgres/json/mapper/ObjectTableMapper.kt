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
            resultSetGetterName = (KotlinType.of(column.type.klass.name)
                ?: error("cannot map to KotlinType: ${column.type.klass.name}")).toJdbcSetterName(),
            columnName = column.column.name,
            fieldName = parentField,
            fieldType = column.type.klass.name,
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
                setterType = (KotlinType.of(it.type.klass.name)
                    ?: error("cannot map to KotlinType: ${it.type.klass.name}")).toJdbcSetterName(),
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
                setterType = (KotlinType.of(c.type.klass.name)
                    ?: error("cannot map to KotlinType: ${c.type.klass.name}")).toJdbcSetterName(),
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
            setterType = (KotlinType.of(it.type.klass.name)
                ?: error("cannot map to KotlinType: ${it.type.klass.name}")).toJdbcSetterName(),
            path = it.path.joinToString("."),
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

private fun converterName(from: QualifiedName, to: QualifiedName): String? {
    if (from == to) return null
    return when (from to to) {
        KotlinType.DATE.qn to SqlType.DATE.qualifiedName -> "postgres.json.lib.toDate"
        SqlType.DATE.qualifiedName to KotlinType.DATE.qn -> "postgres.json.lib.toLocalDate"
        else -> error("No converter from $from to $to")
    }
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


private fun KotlinType.toJdbcSetterName(): String = when (this) {
    KotlinType.BOOLEAN -> "Boolean"
    KotlinType.DOUBLE -> "Double"
    KotlinType.INT -> "Int"
    KotlinType.BIG_DECIMAL -> "BigDecimal"
    KotlinType.STRING -> "String"
    KotlinType.FLOAT -> "Float"
    KotlinType.LONG -> "Long"
    KotlinType.DATE -> "Date"
    KotlinType.TIMESTAMP -> "Timestamp"
    KotlinType.UUID -> "Object"
    //TODO
    else -> error("Unexpected postgres type: $this")
}

private fun flattenToColumns(klass: Klass, path: List<String> = emptyList()): List<ColumnMapping> {
    return klass.fields.flatMap { field ->
        val columnAnnotation = field.annotations.filterIsInstance<Column>().singleOrNull()

        val colType: PostgresType? = columnAnnotation
            ?.type.takeIf { it != PostgresType.NONE }
        //?: typeMappings[field.type.klass.name]

        when {
            colType == null && field.type.klass.fields.isEmpty() -> {
                error("Cannot define PostgresType for field: ${klass.name}.${path.joinToString(".")} of type ${field.type}. Specify type implicitly in @Column")
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

//private val typeMappings = mapOf(
//    QualifiedName(pkg = "kotlin", name = "String") to PostgresType.TEXT,
//    QualifiedName(pkg = "kotlin", name = "Long") to PostgresType.BIGINT,
//    QualifiedName(pkg = "kotlin", name = "Int") to PostgresType.INTEGER,
//    QualifiedName(pkg = "kotlin", name = "Double") to PostgresType.DOUBLE,
//    QualifiedName(pkg = "kotlin", name = "Float") to PostgresType.REAL,
//    QualifiedName(pkg = "kotlin", name = "Boolean") to PostgresType.BOOLEAN,
//    QualifiedName(pkg = "kotlin", name = "ByteArray") to PostgresType.BYTEA,
//    QualifiedName(pkg = "java.math", name = "BigDecimal") to PostgresType.NUMERIC,
//    QualifiedName(pkg = "java.util", name = "UUID") to PostgresType.UUID,
//    QualifiedName(pkg = "java.time", name = "Instant") to PostgresType.TIMESTAMP_WITH_TIMEZONE,//TODO with timezone
//    QualifiedName(pkg = "java.sql", name = "Date") to PostgresType.DATE,
//    QualifiedName(pkg = "java.sql", name = "Timestamp") to PostgresType.TIMESTAMP_WITH_TIMEZONE,
//    QualifiedName(pkg = "java.time", name = "LocalTime") to PostgresType.TIME,
//    QualifiedName(pkg = "java.time", name = "LocalDateTime") to PostgresType.TIMESTAMP,
//    QualifiedName(pkg = "java.time", name = "ZonedDateTime") to PostgresType.TIMESTAMP_WITH_TIMEZONE,
//    //QualifiedName(pkg = "java.time", name = "OffsetDateTime") to "timestamp with time zone",TODO?
//)

enum class SqlType(val qualifiedName: QualifiedName) {
    DATE(QualifiedName("java.sql", "Date")),
    ;
}