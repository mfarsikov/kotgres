package postgres.json

import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.DriverManager
import java.util.*


fun main() {
    val connection = DriverManager.getConnection("jdbc:postgresql://localhost/postgres?user=postgres&password=postgres")

    val repo = IphoneRepository(connection)

   // repo.check()
    repo.save(
        Iphone(
            id = UUID.randomUUID().toString(),
            name = "12",
            version = 12,
            spec = Spec(
                proc = "a13",
                mem = 4,
                battery = Battery(
                    capacity = 300,
                    longivity = "5h"
                )
            )
        )
    )

}


class IphoneRepository(
    private val connection: Connection
) {
//
//    fun check() {
//        val postgresFields = loadPostgresFields("t")
//
//        listOf(FieldDefinition())
//    }
//
//    private fun loadPostgresFields(tableName: String): List<FieldDefinition> {
//        val stmt = connection.prepareStatement(
//            """
//            select column_name, is_nullable, data_type from information_schema.columns where table_name = 't'
//        """.trimIndent()
//        )
//
//        val rs = stmt.executeQuery()
//
//        return sequence {
//            while (rs.next()) {
//                yield(
//                    FieldDefinition(
//                        name = rs.getString(1),
//                        nullable = rs.getBoolean(2),
//                        type = PostgresType.of(rs.getString(3))
//                    )
//                )
//            }
//        }.toList()
//
//    }

    fun save(phone: Iphone) {

        val savePrepStatement = connection.prepareStatement(
            """
                insert into t (id, json) 
                values (?, ?)
            """.trimIndent()
        )

        with(savePrepStatement) {

            setObject(1, PGobject().apply {
                type = "uuid"
                value = phone.id.toString()
            })
            setObject(2, PGobject().apply {
                type = "jsonb"
                value = Json.encodeToString(phone)
            })
            execute()
            close()
        }
    }
}

class UUIDSerializer : KSerializer<UUID> {
    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override val descriptor: SerialDescriptor
        get() = TODO("Not yet implemented")
}


data class Iphone(
    val id: String,
    val name: String,
    val version: Int,
    val spec: Spec
)

data class Spec(
    val proc: String,
    val mem: Int,
    val battery: Battery
)

data class Battery(
    val capacity: Int,
    val longivity: String
)
