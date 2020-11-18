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
import org.postgresql.util.PGobject
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import javax.sql.DataSource


fun main() {
    val ds: DataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://localhost/postgres?user=postgres&password=postgres"
        username = "postgres"
    })

    ds.connection.use { it.createStatement().execute("""
        drop table if exists t;
        create table t (i integer);
    """.trimIndent()) }

    val con = ds.connection
    val ps = con.prepareStatement("insert into t (i) values (?)")
    ps.setNull(1, java.sql.Types.INTEGER)
     ps.execute()



}