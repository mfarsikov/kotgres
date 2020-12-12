package my.pack

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
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

    Flyway.configure().dataSource(ds).load().migrate()

    val db = DB(ds)

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


    db.transaction {
        myClassRepository.turnOnLogsOnServerForCurrentTransaction()
        myClassRepository.saveAll(listOf(item, item.copy(id = "14")))
    }

    println("DONE")
    //db.transaction { myClassRepository.deleteAll() }

    //println("items: ${db.transaction { myClassRepository.findAll() }}")

}