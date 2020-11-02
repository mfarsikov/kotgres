package postgres.json.lib

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

interface Converter<E, D> {
    fun toDatabaseColumn(entity: E): D
    fun toEntityField(database: D): E
}

class NoOpConverter<T>: Converter<T,T>{
    override fun toDatabaseColumn(entity: T) = entity

    override fun toEntityField(database: T) = database

}

class LocalDateConverter : Converter<LocalDate, String> {
    override fun toDatabaseColumn(entity: LocalDate): String {
        return entity.toString()
    }

    override fun toEntityField(database: String): LocalDate {
        return LocalDate.parse(database)
    }
}

fun toString(localDate: LocalDate): String = localDate.toString()
fun toLocalDate(date: Date):LocalDate = date.toLocalDate()
fun toDate(localDate: LocalDate) =Date.valueOf(localDate)

fun main() {
        val ds= HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://localhost/postgres?user=postgres&password=postgres"
        username = "postgres"
    })

    val stmt = ds.connection.prepareStatement("insert into t (ts, tswtz) values (?, ?)")
    stmt.setTimestamp(1, Timestamp.from(Instant.now()))
    stmt.setTimestamp(2, Timestamp.from(Instant.now()))
    stmt.execute()
   // println(LocalDate.now().let { toDate(it) })
}
