package my.pack

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
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
            version = 13
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
    fun `not null method throws if there is no result`(){
        expect<NoSuchElementException> {
            db.transaction(readOnly = true) { iphoneRepository.findByIdOrThrow(phone.id) }
        }
    }

    @Test
    fun `multiple parameters combined with AND`(){
        db.transaction { iphoneRepository.save(phone) }

        val res = db.transaction(readOnly = true) { iphoneRepository.findByIdAndVersion("13", 13) }
        assert(res == phone)

        val res2 = db.transaction(readOnly = true) { iphoneRepository.findByIdAndVersion("13", 14) }
        assert(res2 == null)
    }

    @Test
    fun `@Where annotation works`(){
        db.transaction { iphoneRepository.save(phone) }

        val res = db.transaction(readOnly = true) { iphoneRepository.findByCapacityAndVersion("13wh", 10) }

        assert(res == listOf(phone))

        val res2 = db.transaction(readOnly = true) { iphoneRepository.findByCapacityAndVersion("13wh", 15) }
        assert(res2 == emptyList<Iphone>())

    }
}
