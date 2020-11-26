package my.pack

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource
import kotlin.reflect.full.isSubclassOf
import kotlin.test.fail

object TestUtil {
    val ds: DataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:tc:postgresql://localhost/postgres?user=postgres&password=postgres"
        username = "postgres"
    })

    fun runMigrations() {
        Flyway.configure().dataSource(ds).load().migrate()
    }
}

inline fun <reified E : Throwable> expect(block: () -> Any?):E {
    try {
        val r = block()
        fail("Expected ${E::class.qualifiedName}, but nothing was thrown, and returned: $r")
    } catch (fail: AssertionError) {
        throw fail
    } catch (actual: Throwable) {
        assert(actual::class.isSubclassOf(E::class))
        return actual as E
    }
}

fun all(vararg r: () -> Unit) {

    val exs = r.mapNotNull {
        try {
            it()
            null
        } catch (ex: AssertionError) {
            ex.message
        }
    }
    if (exs.isNotEmpty())
        throw AssertionError(exs.joinToString(separator = "\n\n"))
}