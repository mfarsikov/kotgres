package my.pack

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.postgresql.util.PGobject
import postgres.json.lib.Column
import postgres.json.lib.Id
import postgres.json.lib.PostgresRepository
import postgres.json.lib.Repository
import postgres.json.lib.Table
import postgres.json.lib.Where
import postgres.json.model.db.PostgresType
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import javax.sql.DataSource

@Table
data class Iphone(
    @Id
    @Column(type = PostgresType.TEXT)
    val id: String,
    @Column(type = PostgresType.TEXT)
    val name: String,
    val spec: Spec,
    @Column(type = PostgresType.INTEGER)
    val version: Int,
    @Column(type = PostgresType.BOOLEAN)
    val bool: Boolean,
    @Column(name = "date", PostgresType.DATE)
    val date: Date,
    @Column(type = PostgresType.TIMESTAMP_WITH_TIMEZONE)
    val timestamp: Timestamp,
    @Column(type = PostgresType.UUID)
    val uuid: UUID,
    @Column(type = PostgresType.TIME)
    val time: LocalTime,
    @Column(type = PostgresType.DATE)
    val localDate: LocalDate,
    @Column(type = PostgresType.TIMESTAMP)
    val localDateTime: LocalDateTime,
    @Column(type = PostgresType.JSONB)
    val list: List<String>
)

data class Spec(
    @Column(type = PostgresType.TEXT)
    val proc: String,
    val battery: Battery,
)

data class Battery(
    @Column(name = "cap_city", type = PostgresType.TEXT)
    val capacity: String,
    @Column(type = PostgresType.TEXT)
    val longivity: String,
)

@PostgresRepository
interface IphoneRepository : Repository<Iphone> {

    fun findById(id: String): Iphone?
    fun findByDate(date: Date): List<Iphone>
    fun findByIdOrThrow(id: String): Iphone
    fun findBySpecProc(proc: String): List<Iphone>
    fun findSingleBySpecProc(proc: String): Iphone
    fun findByIdAndVersion(id: String, version: Int): Iphone?
    fun findByTimestamp(timestamp: Timestamp): List<Iphone>
    fun findByUUID(uuid: UUID): Iphone?
    fun findByTime(time: LocalTime): List<Iphone>
    fun findByLocalDate(localDate: LocalDate): List<Iphone>
    fun findByLocalDateTime(localDateTime: LocalDateTime): List<Iphone>

    fun delete(id: String, date: Date)
    fun deleteByDate(date: Date)

    @Where("cap_city = :capacity and :v <= version and date <= :date")
    fun findByCapacityAndVersion(capacity: String, v: Int, date: Date): List<Iphone>

}

fun main() {
    val ds: DataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://localhost/postgres?user=postgres&password=postgres"
        username = "postgres"
    })

   // val message = Flyway.configure().dataSource(ds).load().migrate()

    val prepareStatement = ds.connection.prepareStatement(
        """
                |INSERT INTO "t" 
                |( "v")
                |VALUES (?)
    """.trimMargin()
    )


    @Language("JSON")
    val json = """
        {
            "x": "Y"
        }
    """.trimIndent()

    PGobject().apply {
        type = "jsonb"
        value =json
    }
    prepareStatement.setObject(1, PGobject().apply {
        type = "jsonb"
        value =json
    })
    prepareStatement.executeUpdate()

    val rs = ds.connection.prepareStatement("select v from t").executeQuery()
rs.next()
    println("read: " + rs.getString(1))

}