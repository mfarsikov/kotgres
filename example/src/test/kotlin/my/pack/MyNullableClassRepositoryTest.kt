package my.pack

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

class MyNullableClassRepositoryTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun createTable() {
            TestUtil.runMigrations()
        }

        val phone = MyNullableClass(
            id = "13",
            name = null,
            myNestedClass = MyNullableNestedClass(
                proc = null,
                myNestedNestedClass = MyNullableNestedNestedClass(
                    capacity = null,
                    longivity = null
                )
            ),
            version = null,
            bool = null,
            date = null,
            timestamp = null,
            uuid = null,
            time = null,
            localDate = null,
            localDateTime = null,
            list = listOf("a", "b", "c"),
            enum = Mode.OFF,
        )
    }

    val db = NullableDb(TestUtil.ds)

    @AfterEach
    fun cleanup() {
        db.transaction { myNullableClassRepository.deleteAll() }
    }

    @Test
    fun check() {
        assert(db.check().isEmpty())
    }

    @Test
    fun rollback() {

        db.transaction {
            myNullableClassRepository.save(phone)
            rollback()
        }

        val phones = db.transaction(readOnly = true) { myNullableClassRepository.findAll() }
        assert(phones.isEmpty()) { "rollback does not work" }
    }

    @Test
    fun `rollback on exception`() {
        try {
            db.transaction {
                myNullableClassRepository.save(phone)
                error("")
            }
        } catch (ex: IllegalStateException) {
        }

        val phones = db.transaction(readOnly = true) { myNullableClassRepository.findAll() }
        assert(phones.isEmpty())
    }

    @Test
    fun save() {

        db.transaction {
            myNullableClassRepository.save(phone)
        }

        val phones2 = db.transaction(readOnly = true) { myNullableClassRepository.findAll() }

        assert(phones2 == listOf(phone))
    }

    @Test
    fun saveAll() {

        val phones = listOf(phone, phone.copy(id = "14"))

        db.transaction {
            myNullableClassRepository.saveAll(phones)

        }

        val phones2 = db.transaction(readOnly = true) { myNullableClassRepository.findAll() }

        assert(phones2 == phones)
    }

    @Test
    fun update() {

        db.transaction { myNullableClassRepository.save(phone) }
        db.transaction { myNullableClassRepository.save(phone.copy(name = "iphone2")) }

        val phones = db.transaction(readOnly = true) { myNullableClassRepository.findAll() }

        assert(phones == listOf(phone.copy(name = "iphone2")))
    }

    @Test
    fun `query method returns an entity`() {
        db.transaction { myNullableClassRepository.save(phone) }

        val found = db.transaction(readOnly = true) { myNullableClassRepository.findById(phone.id) }

        assert(found == phone)
    }

    @Test()
    fun `single result query method throws if there are more than one result`() {
        db.transaction { myNullableClassRepository.saveAll(listOf(phone, phone.copy(id = "14"))) }

        expect<IllegalStateException> {
            db.transaction(readOnly = true) { myNullableClassRepository.findSingleBySpecProc(null) }
        }
    }

    @Test
    fun `nullable query method returns null if there is no result`() {

        val found = db.transaction(readOnly = true) { myNullableClassRepository.findById(phone.id) }

        assert(found == null)
    }

    @Test
    fun `not null method throws if there is no result`() {
        expect<NoSuchElementException> {
            db.transaction(readOnly = true) { myNullableClassRepository.findByIdOrThrow(phone.id) }
        }
    }

    @Test
    fun `multiple parameters combined with AND`() {
        db.transaction { myNullableClassRepository.save(phone) }

        fun `find by id and version`(id: String, version: Int?) =
            db.transaction(readOnly = true) { myNullableClassRepository.findByIdAndVersion(id, version) }

        all(
            { assert(`find by id and version`("13", null) == phone) },
            { assert(`find by id and version`("13", 14) == null) },
        )
    }

    @Test
    fun `@Where annotation works`() {
        db.transaction { myNullableClassRepository.save(phone) }

        fun `test @Where`(
            capacity: String?,
            v: Int?,
            date: String?
        ) = db.transaction(readOnly = true) {
            myNullableClassRepository.findByCapacityAndVersion(
                capacity = capacity,
                v = v,
                date = date?.let { Date.valueOf(LocalDate.parse(it)) }
            )
        }

        all(
            { assert(`test @Where`(null, null, null) == listOf(phone)) },
            { assert(`test @Where`("12wh", 12, "2010-01-02") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by timestamp`() {
        db.transaction { myNullableClassRepository.save(phone) }

        fun `find by timestamp`(ts: String?) =
            db.transaction { this.myNullableClassRepository.findByTimestamp(ts?.let { Timestamp.from(Instant.parse(it)) }) }

        all(
            { assert(`find by timestamp`(null) == listOf(phone)) },
            { assert(`find by timestamp`("2010-01-01T00:00:00.001Z") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by uuid`() {
        db.transaction { myNullableClassRepository.save(phone) }

        fun `find by uuid`(uuid: String?) =
            db.transaction { this.myNullableClassRepository.findByUUID(uuid?.let{UUID.fromString(it)}) }

        all(
            { assert(`find by uuid`(null) == phone) },
            { assert(`find by uuid`("00000000-0000-0000-0000-000000000001") == null) },
        )
    }

    @Test
    fun `search by time`() {
        db.transaction { myNullableClassRepository.save(phone) }

        fun `find by time`(time: String?) =
            db.transaction { this.myNullableClassRepository.findByTime(time?.let { LocalTime.parse(it) }) }

        all(
            { assert(`find by time`(null) == listOf(phone)) },
            { assert(`find by time`("00:00:01") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by local date`() {
        db.transaction { myNullableClassRepository.save(phone) }

        fun `find by local date`(time: String?) =
            db.transaction { this.myNullableClassRepository.findByLocalDate(time?.let { LocalDate.parse(it) }) }

        all(
            { assert(`find by local date`(null) == listOf(phone)) },
            { assert(`find by local date`("2010-01-02") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by local date time`() {
        db.transaction { myNullableClassRepository.save(phone) }

        fun `find by local date time`(time: String?) =
            db.transaction { this.myNullableClassRepository.findByLocalDateTime(time?.let { LocalDateTime.parse(it) }) }

        all(
            { assert(`find by local date time`(null) == listOf(phone)) },
            { assert(`find by local date time`("2010-01-02T00:00:00") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by enum`() {
        db.transaction { myNullableClassRepository.save(phone) }

        fun `find by enum`(mode: Mode) =
            db.transaction { this.myNullableClassRepository.findByMode(mode) }

        all(
            { assert(`find by enum`(Mode.OFF) == listOf(phone)) },
            { assert(`find by enum`(Mode.ON) == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `select projection`() {
        db.transaction { myNullableClassRepository.save(phone) }

        fun `find by proc`(proc: String?) =
            db.transaction { this.myNullableClassRepository.selectProjection(proc) }

        all(
            { assert(`find by proc`(null) == NullableProjectionOfMyClass(phone.id, phone.date, phone.list, capacity = null)) },
            { assert(`find by proc`("bionic14") == null) },
        )
    }

    @Test
    fun `select projection in custom query`() {
        db.transaction { myNullableClassRepository.save(phone) }

        fun `find by proc`(proc: String?) =
            db.transaction { this.myNullableClassRepository.selectProjectionCustomQuery(proc) }

        all(
            {
                assert(
                    `find by proc`(null) == NullableProjectionOfMyClass(
                        phone.id,
                        phone.date,
                        listOf("a", "b", "c"),
                        capacity = null
                    )
                )
            },
            { assert(`find by proc`("bionic14") == null) },
        )
    }

    @Test
    fun `select projection in custom where`() {
        db.transaction { myNullableClassRepository.save(phone) }

        fun `find by proc`(proc: String?) =
            db.transaction { this.myNullableClassRepository.selectProjectionWhere(proc) }

        all(
            { assert(`find by proc`(null) == NullableProjectionOfMyClass(phone.id, phone.date, phone.list, capacity = null)) },
            { assert(`find by proc`("bionic14") == null) },
        )
    }

    @Test
    fun `select scalar`() {
        db.transaction { myNullableClassRepository.save(phone) }

        fun `select date by id`(id: String) =
            db.transaction { myNullableClassRepository.selectDate(id) }

        all(
            { assert(`select date by id`("13") == phone.date) },
            { assert(`select date by id`("14") == null) },
        )
    }

    @Test
    fun `select scalars`() {
        db.transaction {
            myNullableClassRepository.save(phone)
            myNullableClassRepository.save(phone.copy(id = "14"))
        }

        fun `select date by proc`(proc: String?) =
            db.transaction { myNullableClassRepository.selectDates(proc) }

        all(
            { assert(`select date by proc`(null) == listOf(phone.date, phone.date)) },
            { assert(`select date by proc`("bionic14") == emptyList<Date>()) },
        )
    }

    @Test
    fun `custom update`() {
        //GIVEN
        db.transaction { myNullableClassRepository.save(phone.copy(date = Date.valueOf("2020-12-31"))) }

        //WHEN
        db.transaction { myNullableClassRepository.update(phone.id,null ) }

        //THEN
        val date = db.transaction { myNullableClassRepository.selectDate(phone.id) }

        assert(date == null)
    }

    @Test
    fun `select IN`() {

        val phones = listOf(phone, phone.copy(id = "14"))
        db.transaction {
            myNullableClassRepository.saveAll(phones)
        }

        fun `id in`(ids: List<String>) =
            db.transaction { myNullableClassRepository.findByIdIn(ids) }

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
            myNullableClassRepository.saveAll(phones)
        }

        fun `id in`(ids: List<String>) =
            db.transaction { myNullableClassRepository.selectProjectionWhere(ids) }

        all(
            {
                assert(
                    `id in`(listOf("13", "14")) == listOf(
                        NullableProjectionOfMyClass(
                            id = phone.id,
                            date = phone.date,
                            list = phone.list,
                            capacity = null
                        ),
                        NullableProjectionOfMyClass(id = "14", date = phone.date, list = phone.list, capacity = null)
                    )
                )
            },
            { assert(`id in`(listOf("15")) == emptyList<MyClass>()) },
            { assert(`id in`(emptyList()) == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `select IN with custom query`() {
        val phones = listOf(phone.copy(date = Date.valueOf("2010-01-01")))
        db.transaction {
            myNullableClassRepository.saveAll(phones)
        }

        fun `dates in`(dates: List<String>) =
            db.transaction { myNullableClassRepository.customSelectWhereDatesIn(dates.map { Date.valueOf(it) }) }

        all(
            {
                assert(
                    `dates in`(listOf("2010-01-01", "2010-01-02")) == listOf(
                        NullableProjectionOfMyClass(id = phone.id, date = Date.valueOf("2010-01-01"), list = phone.list, capacity = null)
                    )
                )
            },
        )
    }

    @Test
    fun `save-read null value`() {
        val noNamePhone = phone.copy(name = null)

        db.transaction { myNullableClassRepository.save(noNamePhone) }
        val fromDb = db.transaction { myNullableClassRepository.findById(noNamePhone.id) }

        assert(fromDb == noNamePhone)
    }

    @Test
    fun `where name is null`() {
        val noNamePhone = phone.copy(name = null)

        db.transaction { myNullableClassRepository.save(noNamePhone) }
        val fromDb = db.transaction { myNullableClassRepository.findByName(null) }

        assert(fromDb == noNamePhone)
    }
}
