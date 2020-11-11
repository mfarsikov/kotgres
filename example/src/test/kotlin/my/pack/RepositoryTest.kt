package my.pack

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import javax.sql.DataSource

class RepositoryTest {

    companion object {
        val ds: DataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:tc:postgresql://localhost/postgres?user=postgres&password=postgres"
            username = "postgres"
        })

        @JvmStatic
        @BeforeAll
        fun createTable() {
            Flyway.configure().dataSource(ds).load().migrate()
        }

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
    }

    val db = DB(ds)

    @AfterEach
    fun cleanup() {
        db.transaction { myClassRepository.deleteAll() }
    }

    @Test
    fun check() {
        assert(db.check().isEmpty())
    }

    @Test
    fun rollback() {

        db.transaction {
            myClassRepository.save(phone)
            rollback()
        }

        val phones = db.transaction(readOnly = true) { myClassRepository.findAll() }
        assert(phones.isEmpty()) { "rollback does not work" }
    }

    @Test
    fun `rollback on exception`() {
        try {
            db.transaction {
                myClassRepository.save(phone)
                error("")
            }
        } catch (ex: IllegalStateException) {
        }

        val phones = db.transaction(readOnly = true) { myClassRepository.findAll() }
        assert(phones.isEmpty())
    }

    @Test
    fun save() {

        db.transaction {
            myClassRepository.save(phone)
        }

        val phones2 = db.transaction(readOnly = true) { myClassRepository.findAll() }

        assert(phones2 == listOf(phone))
    }

    @Test
    fun saveAll() {

        val phones = listOf(phone, phone.copy(id = "14"))

        db.transaction {
            myClassRepository.saveAll(phones)

        }

        val phones2 = db.transaction(readOnly = true) { myClassRepository.findAll() }

        assert(phones2 == phones)
    }

    @Test
    fun update() {

        db.transaction { myClassRepository.save(phone) }
        db.transaction { myClassRepository.save(phone.copy(name = "iphone2")) }

        val phones = db.transaction(readOnly = true) { myClassRepository.findAll() }

        assert(phones == listOf(phone.copy(name = "iphone2")))
    }

    @Test
    fun `query method returns an entity`() {
        db.transaction { myClassRepository.save(phone) }

        val found = db.transaction(readOnly = true) { myClassRepository.findById(phone.id) }

        assert(found == phone)
    }

    @Test()
    fun `single result query method throws if there are more than one result`() {
        db.transaction { myClassRepository.saveAll(listOf(phone, phone.copy(id = "14"))) }

        expect<IllegalStateException> {
            db.transaction(readOnly = true) { myClassRepository.findSingleBySpecProc("bionic13") }
        }
    }

    @Test
    fun `nullable query method returns null if there is no result`() {

        val found = db.transaction(readOnly = true) { myClassRepository.findById(phone.id) }

        assert(found == null)
    }

    @Test
    fun `not null method throws if there is no result`() {
        expect<NoSuchElementException> {
            db.transaction(readOnly = true) { myClassRepository.findByIdOrThrow(phone.id) }
        }
    }

    @Test
    fun `multiple parameters combined with AND`() {
        db.transaction { myClassRepository.save(phone) }

        fun `find by id and version`(id: String, version: Int) =
            db.transaction(readOnly = true) { myClassRepository.findByIdAndVersion(id, version) }

        all(
            { assert(`find by id and version`("13", 13) == phone) },
            { assert(`find by id and version`("13", 14) == null) },
        )
    }

    @Test
    fun `@Where annotation works`() {
        db.transaction { myClassRepository.save(phone) }

        fun `test @Where`(
            capacity: String,
            v: Int,
            date: String
        ) = db.transaction(readOnly = true) {
            myClassRepository.findByCapacityAndVersion(
                capacity = capacity,
                v = v,
                date = Date.valueOf(LocalDate.parse(date))
            )
        }

        all(
            { assert(`test @Where`("13wh", 13, "2010-01-01") == listOf(phone)) },
            { assert(`test @Where`("13wh", 13, "2010-01-02") == listOf(phone)) },
            { assert(`test @Where`("13wh", 12, "2010-01-02") == listOf(phone)) },
            { assert(`test @Where`("12wh", 12, "2010-01-02") == emptyList<MyClass>()) },
            { assert(`test @Where`("13wh", 12, "2009-01-01") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by timestamp`() {
        db.transaction { myClassRepository.save(phone) }

        fun `find by timestamp`(ts: String) =
            db.transaction { this.myClassRepository.findByTimestamp(Timestamp.from(Instant.parse(ts))) }

        all(
            { assert(`find by timestamp`("2010-01-01T00:00:00.000Z") == listOf(phone)) },
            { assert(`find by timestamp`("2010-01-01T00:00:00.001Z") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by uuid`() {
        db.transaction { myClassRepository.save(phone) }

        fun `find by uuid`(uuid: String) =
            db.transaction { this.myClassRepository.findByUUID(UUID.fromString(uuid)) }

        all(
            { assert(`find by uuid`("66832deb-1864-42b1-b057-e65c28d39a4e") == phone) },
            { assert(`find by uuid`("00000000-0000-0000-0000-000000000001") == null) },
        )
    }

    @Test
    fun `search by time`() {
        db.transaction { myClassRepository.save(phone) }

        fun `find by time`(time: String) =
            db.transaction { this.myClassRepository.findByTime(LocalTime.parse(time)) }

        all(
            { assert(`find by time`("00:00:00") == listOf(phone)) },
            { assert(`find by time`("00:00:01") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by local date`() {
        db.transaction { myClassRepository.save(phone) }

        fun `find by local date`(time: String) =
            db.transaction { this.myClassRepository.findByLocalDate(LocalDate.parse(time)) }

        all(
            { assert(`find by local date`("2010-01-01") == listOf(phone)) },
            { assert(`find by local date`("2010-01-02") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by local date time`() {
        db.transaction { myClassRepository.save(phone) }

        fun `find by local date time`(time: String) =
            db.transaction { this.myClassRepository.findByLocalDateTime(LocalDateTime.parse(time)) }

        all(
            { assert(`find by local date time`("2010-01-01T00:00:00") == listOf(phone)) },
            { assert(`find by local date time`("2010-01-02T00:00:00") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by enum`() {
        db.transaction { myClassRepository.save(phone) }

        fun `find by enum`(mode: Mode) =
            db.transaction { this.myClassRepository.findByMode(mode) }

        all(
            { assert(`find by enum`(Mode.OFF) == listOf(phone)) },
            { assert(`find by enum`(Mode.ON) == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `select projection`() {
        db.transaction { myClassRepository.save(phone) }

        fun `find by proc`(proc: String) =
            db.transaction { this.myClassRepository.selectProjection(proc) }

        all(
            { assert(`find by proc`("bionic13") == ProjectionOfMyClass(phone.id, phone.date, listOf("a", "b", "c"))) },
            { assert(`find by proc`("bionic14") == null) },
        )
    }

    @Test
    fun `select projection in custom query`() {
        db.transaction { myClassRepository.save(phone) }

        fun `find by proc`(proc: String) =
            db.transaction { this.myClassRepository.selectProjectionCustomQuery(proc) }

        all(
            { assert(`find by proc`("bionic13") == ProjectionOfMyClass(phone.id, phone.date, listOf("a", "b", "c"))) },
            { assert(`find by proc`("bionic14") == null) },
        )
    }

    @Test
    fun `select projection in custom where`() {
        db.transaction { myClassRepository.save(phone) }

        fun `find by proc`(proc: String) =
            db.transaction { this.myClassRepository.selectProjectionWhere(proc) }

        all(
            { assert(`find by proc`("bionic13") == ProjectionOfMyClass(phone.id, phone.date, phone.list)) },
            { assert(`find by proc`("bionic14") == null) },
        )
    }

    @Test
    fun `select scalar`() {
        db.transaction { myClassRepository.save(phone) }

        fun `select date by id`(id: String) =
            db.transaction { myClassRepository.selectDate(id) }

        all(
            { assert(`select date by id`("13") == phone.date) },
            { assert(`select date by id`("14") == null) },
        )
    }

    @Test
    fun `select scalars`() {
        db.transaction {
            myClassRepository.save(phone)
            myClassRepository.save(phone.copy(id = "14"))
        }

        fun `select date by proc`(proc: String) =
            db.transaction { myClassRepository.selectDates(proc) }

        all(
            { assert(`select date by proc`("bionic13") == listOf(phone.date, phone.date)) },
            { assert(`select date by proc`("bionic14") == emptyList<Date>()) },
        )
    }

    @Test
    fun `custom update`() {
        //GIVEN
        db.transaction { myClassRepository.save(phone) }

        //WHEN
        db.transaction { myClassRepository.update(phone.id, Date.valueOf("2020-12-31")) }

        //THEN
        val date = db.transaction { myClassRepository.selectDate(phone.id) }

        assert(date == Date.valueOf("2020-12-31"))
    }

    @Test
    fun `select IN`() {

        val phones = listOf(phone, phone.copy(id = "14"))
        db.transaction {
            myClassRepository.saveAll(phones)
        }

        fun `id in`(ids: List<String>) =
            db.transaction { myClassRepository.findByIdIn(ids) }

        all(
            { assert(`id in`(listOf("13", "14")) == phones) },
            { assert(`id in`(listOf("15")) == emptyList<MyClass>()) },
            { assert(`id in`(emptyList()) == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `select IN with @Where`() {
        val phones = listOf(phone, phone.copy(id = "14"))
        db.transaction {
            myClassRepository.saveAll(phones)
        }

        fun `id in`(ids: List<String>) =
            db.transaction { myClassRepository.selectProjectionWhereIdIn(ids) }

        all(
            {
                assert(
                    `id in`(listOf("13", "14")) == listOf(
                        ProjectionOfMyClass(
                            id = phone.id,
                            date = phone.date,
                            list = phone.list
                        ), ProjectionOfMyClass(id = "14", date = phone.date, list = phone.list)
                    )
                )
            },
            { assert(`id in`(listOf("15")) == emptyList<MyClass>()) },
            { assert(`id in`(emptyList()) == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `select IN with custom query`() {
        val phones = listOf(phone)
        db.transaction {
            myClassRepository.saveAll(phones)
        }

        fun `dates in`(dates: List<String>) =
            db.transaction { myClassRepository.customSelectWhereDatesIn(dates.map { Date.valueOf(it) }) }

        all(
            {
                assert(
                    `dates in`(listOf("2010-01-01", "2010-01-02")) == listOf(
                        ProjectionOfMyClass(id = phone.id,date = phone.date,list = phone.list )
                    )
                )
            },
        )
    }

    @Test
    fun `save-read null value`(){
        val noNamePhone = phone.copy(name = null)

        db.transaction { myClassRepository.save(noNamePhone) }
        val fromDb = db.transaction { myClassRepository.findById(noNamePhone.id) }

        assert(fromDb == noNamePhone)
    }
    @Test
    fun `where name is null`(){
        val noNamePhone = phone.copy(name = null)

        db.transaction { myClassRepository.save(noNamePhone) }
        val fromDb = db.transaction { myClassRepository.findByName(null) }

        assert(fromDb == noNamePhone)
    }
}
