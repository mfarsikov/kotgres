package my.pack

import kotgres.aux.sort.Order
import kotgres.aux.sort.SortCol
import kotgres.aux.sort.SortOrder
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

        val item = MyNullableClass(
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
            myNullableClassRepository.save(item)
            rollback()
        }

        val items = db.transaction(readOnly = true) { myNullableClassRepository.findAll() }
        assert(items.isEmpty()) { "rollback does not work" }
    }

    @Test
    fun `rollback on exception`() {
        try {
            db.transaction {
                myNullableClassRepository.save(item)
                error("")
            }
        } catch (ex: IllegalStateException) {
        }

        val items = db.transaction(readOnly = true) { myNullableClassRepository.findAll() }
        assert(items.isEmpty())
    }

    @Test
    fun save() {

        db.transaction {
            myNullableClassRepository.save(item)
        }

        val items2 = db.transaction(readOnly = true) { myNullableClassRepository.findAll() }

        assert(items2 == listOf(item))
    }

    @Test
    fun saveAll() {

        val items = listOf(item, item.copy(id = "14"))

        db.transaction {
            myNullableClassRepository.saveAll(items)

        }

        val items2 = db.transaction(readOnly = true) { myNullableClassRepository.findAll() }

        assert(items2 == items)
    }

    @Test
    fun update() {

        db.transaction { myNullableClassRepository.save(item) }
        db.transaction { myNullableClassRepository.save(item.copy(name = "item2")) }

        val items = db.transaction(readOnly = true) { myNullableClassRepository.findAll() }

        assert(items == listOf(item.copy(name = "item2")))
    }

    @Test
    fun `query method returns an entity`() {
        db.transaction { myNullableClassRepository.save(item) }

        val found = db.transaction(readOnly = true) { myNullableClassRepository.findById(item.id) }

        assert(found == item)
    }

    @Test()
    fun `single result query method throws if there are more than one result`() {
        db.transaction { myNullableClassRepository.saveAll(listOf(item, item.copy(id = "14"))) }

        expect<IllegalStateException> {
            db.transaction(readOnly = true) { myNullableClassRepository.findSingleBySpecProc(null) }
        }
    }

    @Test
    fun `nullable query method returns null if there is no result`() {

        val found = db.transaction(readOnly = true) { myNullableClassRepository.findById(item.id) }

        assert(found == null)
    }

    @Test
    fun `not null method throws if there is no result`() {
        expect<NoSuchElementException> {
            db.transaction(readOnly = true) { myNullableClassRepository.findByIdOrThrow(item.id) }
        }
    }

    @Test
    fun `multiple parameters combined with AND`() {
        db.transaction { myNullableClassRepository.save(item) }

        fun `find by id and version`(id: String, version: Int?) =
            db.transaction(readOnly = true) { myNullableClassRepository.findByIdAndVersion(id, version) }

        all(
            { assert(`find by id and version`("13", null) == item) },
            { assert(`find by id and version`("13", 14) == null) },
        )
    }

    @Test
    fun `@Where annotation works`() {
        db.transaction { myNullableClassRepository.save(item) }

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
            { assert(`test @Where`(null, null, null) == listOf(item)) },
            { assert(`test @Where`("12wh", 12, "2010-01-02") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by timestamp`() {
        db.transaction { myNullableClassRepository.save(item) }

        fun `find by timestamp`(ts: String?) =
            db.transaction { this.myNullableClassRepository.findByTimestamp(ts?.let { Timestamp.from(Instant.parse(it)) }) }

        all(
            { assert(`find by timestamp`(null) == listOf(item)) },
            { assert(`find by timestamp`("2010-01-01T00:00:00.001Z") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by uuid`() {
        db.transaction { myNullableClassRepository.save(item) }

        fun `find by uuid`(uuid: String?) =
            db.transaction { this.myNullableClassRepository.findByUUID(uuid?.let { UUID.fromString(it) }) }

        all(
            { assert(`find by uuid`(null) == item) },
            { assert(`find by uuid`("00000000-0000-0000-0000-000000000001") == null) },
        )
    }

    @Test
    fun `search by time`() {
        db.transaction { myNullableClassRepository.save(item) }

        fun `find by time`(time: String?) =
            db.transaction { this.myNullableClassRepository.findByTime(time?.let { LocalTime.parse(it) }) }

        all(
            { assert(`find by time`(null) == listOf(item)) },
            { assert(`find by time`("00:00:01") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by local date`() {
        db.transaction { myNullableClassRepository.save(item) }

        fun `find by local date`(time: String?) =
            db.transaction { this.myNullableClassRepository.findByLocalDate(time?.let { LocalDate.parse(it) }) }

        all(
            { assert(`find by local date`(null) == listOf(item)) },
            { assert(`find by local date`("2010-01-02") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by local date time`() {
        db.transaction { myNullableClassRepository.save(item) }

        fun `find by local date time`(time: String?) =
            db.transaction { this.myNullableClassRepository.findByLocalDateTime(time?.let { LocalDateTime.parse(it) }) }

        all(
            { assert(`find by local date time`(null) == listOf(item)) },
            { assert(`find by local date time`("2010-01-02T00:00:00") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by enum`() {
        db.transaction { myNullableClassRepository.save(item) }

        fun `find by enum`(mode: Mode) =
            db.transaction { this.myNullableClassRepository.findByMode(mode) }

        all(
            { assert(`find by enum`(Mode.OFF) == listOf(item)) },
            { assert(`find by enum`(Mode.ON) == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `select projection`() {
        db.transaction { myNullableClassRepository.save(item) }

        fun `find by proc`(proc: String?) =
            db.transaction { this.myNullableClassRepository.selectProjection(proc) }

        all(
            {
                assert(
                    `find by proc`(null) == NullableProjectionOfMyClass(
                        item.id,
                        item.date,
                        item.list,
                        capacity = null
                    )
                )
            },
            { assert(`find by proc`("bionic14") == null) },
        )
    }

    @Test
    fun `select projection in custom query`() {
        db.transaction { myNullableClassRepository.save(item) }

        fun `find by proc`(proc: String?) =
            db.transaction { this.myNullableClassRepository.selectProjectionCustomQuery(proc) }

        all(
            {
                assert(
                    `find by proc`(null) == NullableProjectionOfMyClass(
                        item.id,
                        item.date,
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
        db.transaction { myNullableClassRepository.save(item) }

        fun `find by proc`(proc: String?) =
            db.transaction { this.myNullableClassRepository.selectProjectionWhere(proc) }

        all(
            {
                assert(
                    `find by proc`(null) == NullableProjectionOfMyClass(
                        item.id,
                        item.date,
                        item.list,
                        capacity = null
                    )
                )
            },
            { assert(`find by proc`("bionic14") == null) },
        )
    }

    @Test
    fun `select scalar`() {
        db.transaction { myNullableClassRepository.save(item) }

        fun `select date by id`(id: String) =
            db.transaction { myNullableClassRepository.selectDate(id) }

        all(
            { assert(`select date by id`("13") == item.date) },
            { assert(`select date by id`("14") == null) },
        )
    }

    @Test
    fun `select scalars`() {
        db.transaction {
            myNullableClassRepository.save(item)
            myNullableClassRepository.save(item.copy(id = "14"))
        }

        fun `select date by proc`(proc: String?) =
            db.transaction { myNullableClassRepository.selectDates(proc) }

        all(
            { assert(`select date by proc`(null) == listOf(item.date, item.date)) },
            { assert(`select date by proc`("bionic14") == emptyList<Date>()) },
        )
    }

    @Test
    fun `custom update`() {
        //GIVEN
        db.transaction { myNullableClassRepository.save(item.copy(date = Date.valueOf("2020-12-31"))) }

        //WHEN
        db.transaction { myNullableClassRepository.update(item.id, null) }

        //THEN
        val date = db.transaction { myNullableClassRepository.selectDate(item.id) }

        assert(date == null)
    }

    @Test
    fun `select IN`() {

        val items = listOf(item, item.copy(id = "14"))
        db.transaction {
            myNullableClassRepository.saveAll(items)
        }

        fun `id in`(ids: List<String>) =
            db.transaction { myNullableClassRepository.findByIdIn(ids) }

        all(
            { assert(`id in`(listOf("13", "14")) == items) },
            { assert(`id in`(listOf("15")) == emptyList<MyClass>()) },
            { assert(`id in`(emptyList()) == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `select IN with @Where`() {
        val items = listOf(item, item.copy(id = "14"))
        db.transaction {
            myNullableClassRepository.saveAll(items)
        }

        fun `id in`(ids: List<String>) =
            db.transaction { myNullableClassRepository.selectProjectionWhere(ids) }

        all(
            {
                assert(
                    `id in`(listOf("13", "14")) == listOf(
                        NullableProjectionOfMyClass(
                            id = item.id,
                            date = item.date,
                            list = item.list,
                            capacity = null
                        ),
                        NullableProjectionOfMyClass(id = "14", date = item.date, list = item.list, capacity = null)
                    )
                )
            },
            { assert(`id in`(listOf("15")) == emptyList<MyClass>()) },
            { assert(`id in`(emptyList()) == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `select IN with custom query`() {
        val items = listOf(item.copy(date = Date.valueOf("2010-01-01")))
        db.transaction {
            myNullableClassRepository.saveAll(items)
        }

        fun `dates in`(dates: List<String>) =
            db.transaction { myNullableClassRepository.customSelectWhereDatesIn(dates.map { Date.valueOf(it) }) }

        all(
            {
                assert(
                    `dates in`(listOf("2010-01-01", "2010-01-02")) == listOf(
                        NullableProjectionOfMyClass(
                            id = item.id,
                            date = Date.valueOf("2010-01-01"),
                            list = item.list,
                            capacity = null
                        )
                    )
                )
            },
        )
    }

    @Test
    fun `save-read null value`() {
        val noNameItem = item.copy(name = null)

        db.transaction { myNullableClassRepository.save(noNameItem) }
        val fromDb = db.transaction { myNullableClassRepository.findById(noNameItem.id) }

        assert(fromDb == noNameItem)
    }

    @Test
    fun `where name is null`() {
        val noNameItem = item.copy(name = null)

        db.transaction { myNullableClassRepository.save(noNameItem) }
        val fromDb = db.transaction { myNullableClassRepository.findByName(null) }

        assert(fromDb == noNameItem)
    }

    @Test
    fun `nullable enum as result type`() {

        db.transaction { myNullableClassRepository.saveAll(listOf(item, item.copy(id = "14", enum = null))) }

        fun enum(id: String): Mode? {
            return db.transaction { myNullableClassRepository.selectEnumWhereId(id) }
        }

        all(
            { assert(enum(item.id) == Mode.OFF) },
            { assert(enum("14") == null) },
        )

    }

    @Test
    fun `select by nullable enum`() {

        db.transaction { myNullableClassRepository.saveAll(listOf(item, item.copy(id = "14", enum = null))) }

        fun enum(enum: Mode?): List<MyNullableClass> {
            return db.transaction { myNullableClassRepository.selectEnumWhereEnum(enum) }
        }

        all(
            { assert(enum(null) == listOf(item.copy(id = "14", enum = null))) },
            { assert(enum(Mode.OFF) == listOf(item)) },
        )

    }

    @Test
    fun `select list of enums`() {
        val items = listOf(
            item,
            item.copy(id = "14", enum = null),
        )

        db.transaction { myNullableClassRepository.saveAll(items) }

        val enums = db.transaction { myNullableClassRepository.findAllEnums() }
        assert(enums.toSet() == setOf(null, Mode.OFF))
    }
}
