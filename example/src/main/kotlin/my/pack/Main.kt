package my.pack

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotgres.annotations.Column
import kotgres.annotations.Id
import kotgres.annotations.PostgresRepository
import kotgres.annotations.Where
import kotgres.aux.PostgresType
import kotgres.aux.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import javax.sql.DataSource

data class MyClass(
    @Id
    val id: String,
    val name: String,
    val myNestedClass: MyNestedClass,
    val version: Int,
    val bool: Boolean,
    val date: Date,
    val timestamp: Timestamp,
    val uuid: UUID,
    val time: LocalTime,
    val localDate: LocalDate,
    val localDateTime: LocalDateTime,
    val list: List<String>,
    val enum: Mode,
)

enum class Mode { ON, OFF }

data class MyNestedClass(
    val proc: String,
    val myNestedNestedClass: MyNestedNestedClass,
)

data class MyNestedNestedClass(
    @Column(name = "cap_city")
    val capacity: String,
    @Column(type = PostgresType.TEXT)
    val longivity: String,
)

@PostgresRepository
interface MyClassRepository : Repository<MyClass> {

    fun findById(id: String): MyClass?
    fun findByDate(date: Date): List<MyClass>
    fun findByIdOrThrow(id: String): MyClass
    fun findBySpecProc(proc: String): List<MyClass>
    fun findSingleBySpecProc(proc: String): MyClass
    fun findByIdAndVersion(id: String, version: Int): MyClass?
    fun findByTimestamp(timestamp: Timestamp): List<MyClass>
    fun findByUUID(uuid: UUID): MyClass?
    fun findByTime(time: LocalTime): List<MyClass>
    fun findByLocalDate(localDate: LocalDate): List<MyClass>
    fun findByLocalDateTime(localDateTime: LocalDateTime): List<MyClass>
    fun findByMode(enum: Mode): List<MyClass>

    fun delete(id: String, date: Date)
    fun deleteByDate(date: Date)

    @Where("cap_city = :capacity and :v <= version and date <= :date")
    fun findByCapacityAndVersion(capacity: String, v: Int, date: Date): List<MyClass>

}

fun main() {
    val ds: DataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://localhost/postgres?user=postgres&password=postgres"
        username = "postgres"
    })

    // val message = Flyway.configure().dataSource(ds).load().migrate()

    val db = DB(ds)

    db.transaction {
        myClassRepository.saveAll(listOf())

    }
}