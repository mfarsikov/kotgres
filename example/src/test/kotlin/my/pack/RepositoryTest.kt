package my.pack

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.*
import javax.sql.DataSource
import kotlin.NoSuchElementException

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

        val phone = Iphone(
            id = "13",
            name = "iphone13",
            spec = Spec(
                proc = "bionic13",
                battery = Battery(
                    capacity = "13wh",
                    longivity = "13h"
                )
            ),
            version = 13,
            bool = true,
            date = java.sql.Date.valueOf(LocalDate.parse("2010-01-01")),
            timestamp = Timestamp.from(Instant.parse("2010-01-01T00:00:00.000Z")),
            uuid = UUID.fromString("66832deb-1864-42b1-b057-e65c28d39a4e"),
            time = Time.valueOf(LocalTime.parse("00:00:00"))
        )
    }

    val db = DB(ds)

    @AfterEach
    fun cleanup() {
        db.transaction { iphoneRepository.deleteAll() }
    }

    @Test
    fun check() {
        assert(db.check().isEmpty())
    }

    @Test
    fun rollback() {

        db.transaction {
            iphoneRepository.save(phone)
            rollback()
        }

        val phones = db.transaction(readOnly = true) { iphoneRepository.findAll() }
        assert(phones.isEmpty()) { "rollback does not work" }
    }

    @Test
    fun `rollback on exception`() {
        try {
            db.transaction {
                iphoneRepository.save(phone)
                error("")
            }
        } catch (ex: IllegalStateException) {
        }

        val phones = db.transaction(readOnly = true) { iphoneRepository.findAll() }
        assert(phones.isEmpty())
    }

    @Test
    fun save() {

        db.transaction {
            iphoneRepository.save(phone)
        }

        val phones2 = db.transaction(readOnly = true) { iphoneRepository.findAll() }

        assert(phones2 == listOf(phone))
    }

    @Test
    fun saveAll() {

        val phones = listOf(phone, phone.copy(id = "14"))

        db.transaction {
            iphoneRepository.saveAll(phones)
        }

        val phones2 = db.transaction(readOnly = true) { iphoneRepository.findAll() }

        assert(phones2 == phones)
    }

    @Test
    fun `query method returns an entity`() {
        db.transaction { iphoneRepository.save(phone) }

        val found = db.transaction(readOnly = true) { iphoneRepository.findById(phone.id) }

        assert(found == phone)
    }

    @Test()
    fun `single result query method throws if there are more than one result`() {
        db.transaction { iphoneRepository.saveAll(listOf(phone, phone.copy(id = "14"))) }

        expect<IllegalStateException> {
            db.transaction(readOnly = true) { iphoneRepository.findSingleBySpecProc("bionic13") }
        }
    }

    @Test
    fun `nullable query method returns null if there is no result`() {

        val found = db.transaction(readOnly = true) { iphoneRepository.findById(phone.id) }

        assert(found == null)
    }

    @Test
    fun `not null method throws if there is no result`() {
        expect<NoSuchElementException> {
            db.transaction(readOnly = true) { iphoneRepository.findByIdOrThrow(phone.id) }
        }
    }

    @Test
    fun `multiple parameters combined with AND`() {
        db.transaction { iphoneRepository.save(phone) }

        fun `find by id and version`(id: String, version: Int) =
            db.transaction(readOnly = true) { iphoneRepository.findByIdAndVersion(id, version) }

        all(
            { assert(`find by id and version`("13", 13) == phone) },
            { assert(`find by id and version`("13", 14) == null) },
        )
    }

    @Test
    fun `@Where annotation works`() {
        db.transaction { iphoneRepository.save(phone) }

        fun `test @Where`(
            capacity: String,
            v: Int,
            date: String
        ) = db.transaction(readOnly = true) {
            iphoneRepository.findByCapacityAndVersion(
                capacity = capacity,
                v = v,
                date = Date.valueOf(LocalDate.parse(date))
            )
        }

        all(
            { assert(`test @Where`("13wh", 13, "2010-01-01") == listOf(phone)) },
            { assert(`test @Where`("13wh", 13, "2010-01-02") == listOf(phone)) },
            { assert(`test @Where`("13wh", 12, "2010-01-02") == listOf(phone)) },
            { assert(`test @Where`("12wh", 12, "2010-01-02") == emptyList<Iphone>()) },
            { assert(`test @Where`("13wh", 12, "2009-01-01") == emptyList<Iphone>()) },
        )
    }

    @Test
    fun `search by timestamp`() {
        db.transaction { iphoneRepository.save(phone) }

        fun `find by timestamp`(ts: String) =
            db.transaction { this.iphoneRepository.findByTimestamp(Timestamp.from(Instant.parse(ts))) }

        all(
            { assert(`find by timestamp`("2010-01-01T00:00:00.000Z") == listOf(phone)) },
            { assert(`find by timestamp`("2010-01-01T00:00:00.001Z") == emptyList<Iphone>()) },
        )
    }

    @Test
    fun `search by uuid`() {
        db.transaction { iphoneRepository.save(phone) }

        fun `find by uuid`(uuid: String) =
            db.transaction { this.iphoneRepository.findByUUID(UUID.fromString(uuid)) }

        all(
            { assert(`find by uuid`("66832deb-1864-42b1-b057-e65c28d39a4e") == phone) },
            { assert(`find by uuid`("00000000-0000-0000-0000-000000000001") == null) },
        )
    }

    @Test
    fun `search by time`() {
        db.transaction { iphoneRepository.save(phone) }

        fun `find by time`(time: String) =
            db.transaction { this.iphoneRepository.findByTime(Time.valueOf(LocalTime.parse(time))) }

        all(
            { assert(`find by time`("00:00:00") == listOf(phone) )},
            { assert(`find by time`("00:00:01") == emptyList<Iphone>()) },
        )
    }
}
