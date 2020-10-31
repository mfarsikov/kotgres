package my.pack

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import postgres.json.lib.Column
import postgres.json.lib.Id
import postgres.json.lib.IsolationLevel
import postgres.json.lib.PostgresRepository
import postgres.json.lib.Repository
import postgres.json.lib.Table
import postgres.json.lib.Where
import postgres.json.model.db.PostgresType
import javax.sql.DataSource

@Table
data class Iphone(
    @Id
    val id: String,
    val name: String,
    val spec: Spec,
    val version: Int
)

data class Spec(
    val proc: String,
    val battery: Battery,
)

data class Battery(
    @Column(name = "cap_city", type = PostgresType.TEXT)
    val capacity: String,
    val longivity: String,
)

@PostgresRepository
interface IphoneRepository : Repository<Iphone> {

    fun findById(id: String): Iphone?
    fun findByIdOrThrow(id: String): Iphone
    fun findBySpecProc(proc: String): List<Iphone>
    fun findSingleBySpecProc(proc: String): Iphone
    fun findByIdAndVersion(id: String, version: Int): Iphone?

    fun delete(id: String)

    @Where("cap_city = :capacity and version >= :v")
    fun findByCapacityAndVersion(capacity: String, v: Int): List<Iphone>

}

fun main() {
    val ds: DataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://localhost/postgres?user=postgres&password=postgres"
        username = "postgres"
    })

    val message = Flyway.configure().dataSource(ds).load().migrate()
    println("migrations executed: ${message.migrationsExecuted}")
    println("warnings: ${message.warnings}")

    val db = DB(ds)

    println("errors: ${db.check()}")

    db.transaction(readOnly = true, IsolationLevel.SERIALIZABLE) {

        iphoneRepository.findAll()

        iphoneRepository.saveAll(
            listOf(
                Iphone(
                    id = "rst2",
                    name = "rst",
                    spec = Spec(
                        proc = "rst",
                        battery = Battery(
                            capacity = "rst",
                            longivity = "rst"
                        )
                    ),
                    version = 10
                )
            )
        )

        println("all: ${iphoneRepository.findAll()}")
        println("by ID: ${iphoneRepository.findById("rst")}")
        println(iphoneRepository.findById("rst2"))
    }

    val res = db.transaction(readOnly = true) { iphoneRepository.findAll() }

    println(res)
}
