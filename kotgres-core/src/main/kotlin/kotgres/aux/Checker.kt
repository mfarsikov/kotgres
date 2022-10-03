package kotgres.aux

import java.sql.Connection

object Checker {

    fun check(tableName: String, expectedColumns: List<ColumnDefinition>, connection: Connection): String? {
        val tableExists = connection.prepareStatement(
            """
                 SELECT exists(
                    SELECT FROM information_schema.tables 
                        WHERE table_name = ?
                 )
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, tableName)
            stmt.executeQuery()
                .use { rs ->
                    rs.next()
                    rs.getBoolean(1)
                }
        }

        if (!tableExists) return "table $tableName does not exist"

        val dbColumns = connection.prepareStatement(
            """
                SELECT
                    c.column_name,
                    c.is_nullable,
                    c.data_type,
                    tc.constraint_type IS NOT NULL AS primary_key
                FROM information_schema.columns c
                LEFT JOIN information_schema.constraint_column_usage ccu 
                       ON c.column_name = ccu.column_name
                LEFT JOIN information_schema.table_constraints tc
                       ON ccu.constraint_schema = tc.constraint_schema
                              and ccu.constraint_name = tc.constraint_name
                              and tc.constraint_type = 'PRIMARY KEY'
                WHERE c.table_name = ?
            """.trimIndent(),
        )
            .use { stmt ->
                stmt.setString(1, tableName)
                stmt.executeQuery()
                    .use { rs ->
                        val dbColumns = mutableSetOf<ColumnDefinition>()
                        while (rs.next()) {
                            dbColumns += ColumnDefinition(
                                name = rs.getString("column_name"),
                                nullable = rs.getBoolean("is_nullable"),
                                type = PostgresType.of(rs.getString("data_type")),
                                isId = rs.getBoolean("primary_key"),
                                isVersion = false,
                            )
                        }
                        dbColumns
                    }
            }

        val dbColumnsByName = dbColumns.associateBy { it.name }
        val expectedColumnByName = expectedColumns.associateBy { it.name }

        val missingErrors = message(
            "Missing columns",
            tableName,
            expectedColumns.filter { it.name !in dbColumnsByName },
        )

        val extraErrors = message(
            "Extra columns",
            tableName,
            dbColumns.filter { it.name !in expectedColumnByName },
        )

        val invalidNullability = message(
            "Invalid nullability",
            tableName,
            dbColumns.filter {
                val expectedCol = expectedColumnByName[it.name]
                if (expectedCol != null) expectedCol.nullable != it.nullable else false
            },
        )

        val invalidType = message(
            "Invalid type",
            tableName,
            dbColumns.filter {
                val expectedCol = expectedColumnByName[it.name]
                if (expectedCol != null) expectedCol.type != it.type else false
            },
        )

        val extraKeys = message(
            "Extra keys",
            tableName,
            dbColumns.filter { it.isId }
                .filter {
                    val expectedCol = expectedColumnByName[it.name]
                    if (expectedCol != null) expectedCol.isId != it.isId else false
                },
        )

        val missingKeys = message(
            "Missing keys",
            tableName,
            expectedColumns.filter { it.isId }
                .filter {
                    val dbCol = dbColumnsByName[it.name]
                    if (dbCol != null) dbCol.isId != it.isId else false
                },
        )

        return listOfNotNull(
            missingErrors,
            extraErrors,
            invalidNullability,
            invalidType,
            extraKeys,
            missingKeys,
        ).joinToString("; ")
            .takeIf { it.isNotEmpty() }
    }

    private fun message(msg: String, tableName: String, list: List<ColumnDefinition>): String? {
        return list.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "(", separator = ", ", postfix = ")")
            ?.let { "$msg: table $tableName $it" }
    }
}
