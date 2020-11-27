package my.pack

import kotgres.aux.page.Page
import kotgres.aux.page.Pageable
import kotgres.aux.sort.NullsOrder
import kotgres.aux.sort.Order
import kotgres.aux.sort.SortCol
import kotgres.aux.sort.SortOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

class MyClassRepositoryTest {

    companion object {

        @JvmStatic
        @BeforeAll
        fun createTable() {
            TestUtil.runMigrations()
        }

        val item = MyClass(
            id = "13",
            name = "item13",
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
            nullableInt = null
        )
    }

    val db = DB(TestUtil.ds)

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
            myClassRepository.save(item)
            rollback()
        }

        val items = db.transaction(readOnly = true) { myClassRepository.findAll() }
        assert(items.isEmpty()) { "rollback does not work" }
    }

    @Test
    fun `rollback on exception`() {
        try {
            db.transaction {
                myClassRepository.save(item)
                error("")
            }
        } catch (ex: IllegalStateException) {
        }

        val items = db.transaction(readOnly = true) { myClassRepository.findAll() }
        assert(items.isEmpty())
    }

    @Test
    fun save() {

        db.transaction {
            myClassRepository.save(item)
        }

        val items2 = db.transaction(readOnly = true) { myClassRepository.findAll() }

        assert(items2 == listOf(item))
    }

    @Test
    fun saveAll() {

        val items = listOf(item, item.copy(id = "14"))

        db.transaction {
            myClassRepository.saveAll(items)

        }

        val items2 = db.transaction(readOnly = true) { myClassRepository.findAll() }

        assert(items2 == items)
    }

    @Test
    fun `fail on conflict`() {

        db.transaction {
            myClassRepository.saveOrFail(item)
        }

        val msg = expect<PSQLException> {
            db.transaction { myClassRepository.saveOrFail(item) }
        }.message

        assert("duplicate key value violates unique constraint" in msg!!)
    }

    @Test
    fun update() {

        db.transaction { myClassRepository.save(item) }
        db.transaction { myClassRepository.save(item.copy(name = "item2")) }

        val items = db.transaction(readOnly = true) { myClassRepository.findAll() }

        assert(items == listOf(item.copy(name = "item2")))
    }

    @Test
    fun `query method returns an entity`() {
        db.transaction { myClassRepository.save(item) }

        val found = db.transaction(readOnly = true) { myClassRepository.findById(item.id) }

        assert(found == item)
    }

    @Test()
    fun `single result query method throws if there are more than one result`() {
        db.transaction { myClassRepository.saveAll(listOf(item, item.copy(id = "14"))) }

        expect<IllegalStateException> {
            db.transaction(readOnly = true) { myClassRepository.findSingleBySpecProc("bionic13") }
        }
    }

    @Test
    fun `nullable query method returns null if there is no result`() {

        val found = db.transaction(readOnly = true) { myClassRepository.findById(item.id) }

        assert(found == null)
    }

    @Test
    fun `not null method throws if there is no result`() {
        expect<NoSuchElementException> {
            db.transaction(readOnly = true) { myClassRepository.findSingleById(item.id) }
        }
    }

    @Test
    fun `multiple parameters combined with AND`() {
        db.transaction { myClassRepository.save(item) }

        fun `find by id and version`(id: String, version: Int) =
            db.transaction(readOnly = true) { myClassRepository.findByIdAndVersion(id, version) }

        all(
            { assert(`find by id and version`("13", 13) == item) },
            { assert(`find by id and version`("13", 14) == null) },
        )
    }

    @Test
    fun `@Where annotation works`() {
        db.transaction { myClassRepository.save(item) }

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
            { assert(`test @Where`("13wh", 13, "2010-01-01") == listOf(item)) },
            { assert(`test @Where`("13wh", 13, "2010-01-02") == listOf(item)) },
            { assert(`test @Where`("13wh", 12, "2010-01-02") == listOf(item)) },
            { assert(`test @Where`("12wh", 12, "2010-01-02") == emptyList<MyClass>()) },
            { assert(`test @Where`("13wh", 12, "2009-01-01") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by timestamp`() {
        db.transaction { myClassRepository.save(item) }

        fun `find by timestamp`(ts: String) =
            db.transaction { this.myClassRepository.findByTimestamp(Timestamp.from(Instant.parse(ts))) }

        all(
            { assert(`find by timestamp`("2010-01-01T00:00:00.000Z") == listOf(item)) },
            { assert(`find by timestamp`("2010-01-01T00:00:00.001Z") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by uuid`() {
        db.transaction { myClassRepository.save(item) }

        fun `find by uuid`(uuid: String) =
            db.transaction { this.myClassRepository.findByUUID(UUID.fromString(uuid)) }

        all(
            { assert(`find by uuid`("66832deb-1864-42b1-b057-e65c28d39a4e") == item) },
            { assert(`find by uuid`("00000000-0000-0000-0000-000000000001") == null) },
        )
    }

    @Test
    fun `search by time`() {
        db.transaction { myClassRepository.save(item) }

        fun `find by time`(time: String) =
            db.transaction { this.myClassRepository.findByTime(LocalTime.parse(time)) }

        all(
            { assert(`find by time`("00:00:00") == listOf(item)) },
            { assert(`find by time`("00:00:01") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by local date`() {
        db.transaction { myClassRepository.save(item) }

        fun `find by local date`(time: String) =
            db.transaction { this.myClassRepository.findByLocalDate(LocalDate.parse(time)) }

        all(
            { assert(`find by local date`("2010-01-01") == listOf(item)) },
            { assert(`find by local date`("2010-01-02") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by local date time`() {
        db.transaction { myClassRepository.save(item) }

        fun `find by local date time`(time: String) =
            db.transaction { this.myClassRepository.findByLocalDateTime(LocalDateTime.parse(time)) }

        all(
            { assert(`find by local date time`("2010-01-01T00:00:00") == listOf(item)) },
            { assert(`find by local date time`("2010-01-02T00:00:00") == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `search by enum`() {
        db.transaction { myClassRepository.save(item) }

        fun `find by enum`(mode: Mode) =
            db.transaction { this.myClassRepository.findByMode(mode) }

        all(
            { assert(`find by enum`(Mode.OFF) == listOf(item)) },
            { assert(`find by enum`(Mode.ON) == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `select projection`() {
        db.transaction { myClassRepository.save(item) }

        fun `find by proc`(proc: String) =
            db.transaction { this.myClassRepository.selectProjection(proc) }

        all(
            { assert(`find by proc`("bionic13") == ProjectionOfMyClass(item.id, item.date, listOf("a", "b", "c"))) },
            { assert(`find by proc`("bionic14") == null) },
        )
    }

    @Test
    fun `select projection in custom query`() {
        db.transaction { myClassRepository.save(item) }

        fun `find by proc`(proc: String) =
            db.transaction { this.myClassRepository.selectProjectionCustomQuery(proc) }

        all(
            { assert(`find by proc`("bionic13") == ProjectionOfMyClass(item.id, item.date, listOf("a", "b", "c"))) },
            { assert(`find by proc`("bionic14") == null) },
        )
    }

    @Test
    fun `select projection in custom where`() {
        db.transaction { myClassRepository.save(item) }

        fun `find by proc`(proc: String) =
            db.transaction { this.myClassRepository.selectProjectionWhere(proc) }

        all(
            { assert(`find by proc`("bionic13") == ProjectionOfMyClass(item.id, item.date, item.list)) },
            { assert(`find by proc`("bionic14") == null) },
        )
    }

    @Test
    fun `select scalar`() {
        db.transaction { myClassRepository.save(item) }

        fun `select date by id`(id: String) =
            db.transaction { myClassRepository.selectDate(id) }

        all(
            { assert(`select date by id`("13") == item.date) },
            { assert(`select date by id`("14") == null) },
        )
    }

    @Test
    fun `select scalars`() {
        db.transaction {
            myClassRepository.save(item)
            myClassRepository.save(item.copy(id = "14"))
        }

        fun `select date by proc`(proc: String) =
            db.transaction { myClassRepository.selectDates(proc) }

        all(
            { assert(`select date by proc`("bionic13") == listOf(item.date, item.date)) },
            { assert(`select date by proc`("bionic14") == emptyList<Date>()) },
        )
    }

    @Test
    fun `custom update`() {
        //GIVEN
        db.transaction { myClassRepository.save(item) }

        //WHEN
        db.transaction { myClassRepository.update(item.id, Date.valueOf("2020-12-31")) }

        //THEN
        val date = db.transaction { myClassRepository.selectDate(item.id) }

        assert(date == Date.valueOf("2020-12-31"))
    }

    @Test
    fun `select IN`() {

        val items = listOf(item, item.copy(id = "14"))
        db.transaction {
            myClassRepository.saveAll(items)
        }

        fun `id in`(ids: List<String>) =
            db.transaction { myClassRepository.findByIdIn(ids) }

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
            myClassRepository.saveAll(items)
        }

        fun `id in`(ids: List<String>) =
            db.transaction { myClassRepository.selectProjectionWhere(ids) }

        all(
            {
                assert(
                    `id in`(listOf("13", "14")) == listOf(
                        ProjectionOfMyClass(
                            id = item.id,
                            date = item.date,
                            list = item.list
                        ), ProjectionOfMyClass(id = "14", date = item.date, list = item.list)
                    )
                )
            },
            { assert(`id in`(listOf("15")) == emptyList<MyClass>()) },
            { assert(`id in`(emptyList()) == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `select IN with custom query`() {
        val items = listOf(item)
        db.transaction {
            myClassRepository.saveAll(items)
        }

        fun `dates in`(dates: List<String>) =
            db.transaction { myClassRepository.customSelectWhereDatesIn(dates.map { Date.valueOf(it) }) }

        all(
            {
                assert(
                    `dates in`(listOf("2010-01-01", "2010-01-02")) == listOf(
                        ProjectionOfMyClass(id = item.id, date = item.date, list = item.list)
                    )
                )
            },
        )
    }

    @Test
    fun `save-read null value`() {
        val noNameItem = item.copy(name = null)

        db.transaction { myClassRepository.save(noNameItem) }
        val fromDb = db.transaction { myClassRepository.findById(noNameItem.id) }

        assert(fromDb == noNameItem)
    }

    @Test
    fun `where name is null`() {
        val noNameItem = item.copy(name = null)

        db.transaction { myClassRepository.save(noNameItem) }
        val fromDb = db.transaction { myClassRepository.findFirstByName(null) }

        assert(fromDb == noNameItem)
    }

    @Test
    fun `find first does not fail on multiple results`() {
        val noNameItem1 = item.copy(name = null)
        val noNameItem2 = item.copy(id = "14", name = null)

        db.transaction { myClassRepository.saveAll(listOf(noNameItem1, noNameItem2)) }
        val fromDb = db.transaction { myClassRepository.findFirstByName(null) }

        assert(fromDb != null)
    }

    @Test
    fun `delete by date`() {

        db.transaction { myClassRepository.save(item) }
        db.transaction { myClassRepository.deleteByDate(item.date) }
        val fromDb = db.transaction { myClassRepository.findAll() }

        assert(fromDb.isEmpty())
    }

    @Test
    fun `limit by 3 elemets`() {
        val fourItems = listOf(
            item,
            item.copy(id = "14"),
            item.copy(id = "15"),
            item.copy(id = "16"),
        )

        db.transaction { myClassRepository.saveAll(fourItems) }

        val limited = db.transaction { myClassRepository.findByDate(item.date) }
        assert(limited.size == 3)
    }

    @Test
    fun `limit by arbitrary amount`() {
        val fourItems = listOf(
            item,
            item.copy(id = "14"),
            item.copy(id = "15"),
            item.copy(id = "16"),
        )

        db.transaction { myClassRepository.saveAll(fourItems) }

        fun limit(limit: Int) = db.transaction { myClassRepository.findByDate(item.date, limit = limit) }

        all(
            { assert(limit(0).size == 0) },
            { assert(limit(3).size == 3) },
            { assert(limit(4).size == 4) },
            { assert(limit(5).size == 4) },
        )
    }

    @Test
    fun `select list of enums`() {
        val items = listOf(
            item,
            item.copy(id = "14", enum = Mode.ON),
        )

        db.transaction { myClassRepository.saveAll(items) }

        val enums = db.transaction { myClassRepository.findAllEnums() }
        assert(enums.toSet() == setOf(Mode.ON, Mode.OFF))
    }

    @Test
    fun `pagination for simple query method`() {
        val items = listOf(
            item,
            item.copy(id = "14"),
            item.copy(id = "15"),
            item.copy(id = "16"),
            item.copy(id = "17"),
            item.copy(id = "18"),
        )

        db.transaction { myClassRepository.saveAll(items) }

        fun query(pageable: Pageable): Page<MyClass> {
            return db.transaction { myClassRepository.findByNamePaged(name = "item13", pageable = pageable) }
        }

        all(
            { assert(query(Pageable(0, 3)).content.size == 3) },
            { assert(query(Pageable(1, 2)).content.size == 2) },
            { assert(query(Pageable(0, 10)).content.size == 6) },
            { assert(query(Pageable(1, 10)).content.size == 0) },
        )
    }

    @Test
    fun `pagination for method with @Where`() {
        val items = listOf(
            item,
            item.copy(id = "14"),
            item.copy(id = "15"),
            item.copy(id = "16"),
            item.copy(id = "17"),
            item.copy(id = "18"),
        )

        db.transaction { myClassRepository.saveAll(items) }

        fun query(pageable: Pageable): Page<MyClass> {
            return db.transaction { myClassRepository.findByNamePagedWhere(name = "item13", pageable = pageable) }
        }

        all(
            { assert(query(Pageable(0, 3)).content == items.take(3)) },
            { assert(query(Pageable(1, 2)).content == items.drop(2).take(2)) },
            { assert(query(Pageable(0, 10)).content == items) },
            { assert(query(Pageable(1, 10)).content == emptyList<MyClass>()) },
        )
    }

    @Test
    fun `pagination for method with custom @Query`() {
        val items = listOf(
            item,
            item.copy(id = "14"),
            item.copy(id = "15"),
            item.copy(id = "16"),
            item.copy(id = "17"),
            item.copy(id = "18"),
        )

        val projection = ProjectionOfMyClass(
            id = "13",
            date = Date.valueOf(LocalDate.parse("2010-01-01")),
            list = listOf("a", "b", "c"),
        )

        val projections = listOf(
            projection,
            projection.copy(id = "14"),
            projection.copy(id = "15"),
            projection.copy(id = "16"),
            projection.copy(id = "17"),
            projection.copy(id = "18"),
        )

        db.transaction { myClassRepository.saveAll(items) }

        fun query(pageable: Pageable): Page<ProjectionOfMyClass> {
            return db.transaction { myClassRepository.findByNamePagedCustom(name = "item13", pageable = pageable) }
        }

        all(
            { assert(query(Pageable(0, 3)).content == projections.take(3)) },
            { assert(query(Pageable(1, 2)).content == projections.drop(2).take(2)) },
            { assert(query(Pageable(0, 10)).content == projections) },
            { assert(query(Pageable(1, 10)).content == emptyList<ProjectionOfMyClass>()) },
        )
    }

    @Test
    fun count() {
        db.transaction { myClassRepository.saveAll(listOf(item, item.copy(id = "14"))) }

        val count = db.transaction { myClassRepository.count() }

        assert(count == 2)
    }


    @Test
    fun exists() {
        db.transaction { myClassRepository.save(item) }

        fun exists(id: String) = db.transaction { myClassRepository.exists(id) }

        all(
            { assert(exists("13")) },
            { assert(!exists("14")) },
        )
    }

    @Test
    fun order() {

        val items = listOf(
            item,
            item.copy(id = "14", name = "item12"),
            item.copy(id = "15", name = null),
        )

        db.transaction { myClassRepository.saveAll(items) }

        fun select(order: Order) = db.transaction { myClassRepository.findAll(order) }

        all(
            {
                val actual = select(Order(listOf(SortCol("id")))).map { it.id }
                assert(actual == listOf("13", "14", "15")) { "order by id" }
            },
            {
                val actual = select(Order(listOf(SortCol("id", SortOrder.DESC)))).map { it.id }
                assert(actual == listOf("15", "14", "13")) { "order by id desc" }
            },
            {
                val actual = select(Order(listOf(SortCol("name")))).map { it.name }
                assert(actual == listOf("item12", "item13", null)) { "order by name" }
            },
            {
                val actual = select(Order(listOf(SortCol("name", SortOrder.DESC)))).map { it.name }
                assert(actual == listOf(null, "item13", "item12")) { "order by name desc" }
            },
            {


                val actual = select(
                    Order(
                        listOf(
                            SortCol(
                                "name",
                                SortOrder.DESC,
                                NullsOrder.NULLS_FIRST
                            )
                        )
                    )
                ).map { it.name }
                assert(actual == listOf(null, "item13", "item12")) { "order by name desc nulls first" }
            },
        )
    }
}
