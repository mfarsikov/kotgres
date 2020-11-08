package kotgres.lib

import kotgres.model.db.ColumnDefinition
import kotgres.model.db.PostgresType
import java.sql.Connection

object Checker {

    fun check(tableName: String, columns: List<ColumnDefinition>, connection: Connection): String? {
        val tableExists = connection.prepareStatement(
            """
                 SELECT exists(
                    SELECT FROM information_schema.tables 
                        WHERE table_name = ?
                 )
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, tableName)
            stmt.executeQuery()
        }.use { rs ->
            rs.next()
            rs.getBoolean(1)
        }

        if (!tableExists) return "table $tableName does not exist"

        val dbColumns = connection.prepareStatement(
            """
                SELECT 
                    column_name, 
                    is_nullable, 
                    data_type 
                FROM information_schema.columns 
                WHERE table_name = ?
            """.trimIndent()
        )
            .use { stmt ->
                stmt.setString(1, tableName)
                stmt.executeQuery()
            }
            .use { rs ->
                val dbColumns = mutableSetOf<ColumnDefinition>()
                while (rs.next()) {
                    dbColumns += ColumnDefinition(
                        name = rs.getString("column_name"),
                        nullable = rs.getBoolean("is_nullable"),
                        type = PostgresType.of(rs.getString("data_type")),
                        isId = false//TODO
                    )
                }
                dbColumns
            }

        val expectedColumns = columns.toSet()
        val missingColumns = expectedColumns - dbColumns
        val extraColumns = dbColumns - expectedColumns


        val missingErrors = missingColumns
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "(", separator = ", ", postfix = ")")
            ?.let { "Missing columns: table $tableName $it" }

        val extraErrors = extraColumns
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "(", separator = ", ", postfix = ")")
            ?.let { "Extra columns: table $tableName $it" }

        return listOfNotNull(missingErrors, extraErrors).joinToString("; ").takeIf { it.isNotEmpty() }
    }
}