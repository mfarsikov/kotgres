package my.pack

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotgres.annotations.Column
import kotgres.annotations.Id
import kotgres.annotations.PostgresRepository
import kotgres.annotations.Query
import kotgres.annotations.Where
import kotgres.aux.IsolationLevel
import kotgres.aux.PostgresType
import kotgres.aux.Repository
import org.flywaydb.core.Flyway
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import javax.sql.DataSource

data class MyClass(
    @Id
    val id: String,
    val name: String?,
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

data class ProjectionOfMyClass(val id: String, val date: Date, val list: List<String>)

@PostgresRepository
interface MyClassRepository : Repository<MyClass> {

    fun findById(id: String): MyClass?
    fun findByName(name: String?): MyClass?
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

   fun findByIdIn(id: List<String>): List<MyClass>

    fun delete(id: String, date: Date)
    fun deleteByDate(date: Date)

    @Where("cap_city = :capacity and date <= :date and :v <= version")
    fun findByCapacityAndVersion(capacity: String, v: Int, date: Date): List<MyClass>

    fun selectProjection(proc: String): ProjectionOfMyClass?

    @Query("select id, date, list from my_class where proc = :proc")
    fun selectProjectionCustomQuery(proc: String): ProjectionOfMyClass?

    @Where("proc = :proc")
    fun selectProjectionWhere(proc: String): ProjectionOfMyClass?

    @Where("id = ANY (:id)")
    fun selectProjectionWhere(id: List<String>): List<ProjectionOfMyClass>

    @Query("select date from my_class where id = :id")
    fun selectDate(id: String): Date?

    @Query("select date from my_class where proc = :proc")
    fun selectDates(proc: String): List<Date>

    @Query("update my_class set date = :date where id = :id")
    fun update(id: String, date: Date)

    @Query("select id, date, list from my_class where date = ANY (:date)")
    fun customSelectWhereDatesIn(date: List<Date>): List<ProjectionOfMyClass>
}

fun main() {
    val ds: DataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://localhost/postgres?user=postgres&password=postgres"
        username = "postgres"
    })

    val message = Flyway.configure().dataSource(ds).load().migrate()

    val db = DB(ds)

    val phone = MyClass(
        id = "13",
        name = "iphone13",
        myNestedClass = MyNestedClass(
            proc = "bionic13",
            myNestedNestedClass = MyNestedNestedClass(
                capacity = "13wh",
                longivity = "13h"
            )
        ),
        version = 13,
        bool = true,
        date = Date.valueOf(LocalDate.parse("2010-01-01")),
        timestamp = Timestamp.from(Instant.parse("2010-01-01T00:00:00.000Z")),
        uuid = UUID.fromString("66832deb-1864-42b1-b057-e65c28d39a4e"),
        time = LocalTime.parse("00:00:00"),
        localDate = LocalDate.parse("2010-01-01"),
        localDateTime = LocalDateTime.parse("2010-01-01T00:00:00"),
        list = listOf("a", "b", "c"),
        enum = Mode.OFF,
    )

    db.transaction(readOnly = true, isolationLevel = IsolationLevel.SERIALIZABLE) {
        myClassRepository.save(phone)
    }

    val con = ds.connection
    val ps = con.prepareStatement("select * from my_class where id = ANY (?)")
    ps.setArray(1, con.createArrayOf("varchar", listOf("13", "14").toTypedArray()))
    val rs = ps.executeQuery()
    while (rs.next()){
        println("id: ${rs.getString("id")}")
    }

}