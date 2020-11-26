package my.pack

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
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
        nullableInt = null
    )

    db.transaction { myClassRepository.saveAll(listOf(phone)) }


    val phones = db.transaction { myClassRepository.findAll() }

}