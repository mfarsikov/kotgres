package my.pack

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import javax.sql.DataSource

class ValidationTest {
    companion object {
        val ds: DataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:tc:postgresql://localhost/postgres?user=postgres&password=postgres"
            username = "postgres"
        })
    }
    @AfterEach
    fun cleanup(){
        ds.connection.use { it.createStatement().execute("""
            drop table validation_entity
        """.trimIndent()) }
    }

    @Test
    fun `happy path`(){
        ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid primary key,
                name text not null,
                age integer
            )
        """.trimIndent()) }
        val db = ValidationDB(ds)
        assert(db.check() == emptyList<String>())
    }

    @Test
    fun `missing primary key`(){
        ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid not null,
                name text not null,
                age integer
            )
        """.trimIndent()) }
        val db = ValidationDB(ds)
        assert(db.check() == listOf("Missing keys: table validation_entity (id UUID not null)"))
    }

    @Test
    fun `extra primary key`(){
        ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid,
                name text,
                age integer,
                primary key (id, name)
            )
        """.trimIndent()) }
        val db = ValidationDB(ds)
        assert(db.check() == listOf("Extra keys: table validation_entity (name TEXT not null)"))
    }

    @Test
    fun `missing column`(){
        ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid primary key,
                age integer
            )
        """.trimIndent()) }
        val db = ValidationDB(ds)
        assert(db.check() == listOf("Missing columns: table validation_entity (name TEXT not null)"))
    }

    @Test
    fun `extra column`(){
        ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid primary key,
                name text not null,
                age integer,
                extra int
            )
        """.trimIndent()) }
        val db = ValidationDB(ds)
        assert(db.check() == listOf("Extra columns: table validation_entity (extra INTEGER)"))
    }

    @Test
    fun `invalid nullable column`(){
        ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid primary key,
                name text,
                age integer
            )
        """.trimIndent()) }
        val db = ValidationDB(ds)
        assert(db.check() == listOf("Invalid nullability: table validation_entity (name TEXT)"))
    }

    @Test
    fun `invalid not nullable column`(){
        ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid primary key,
                name text not null,
                age integer not null
            )
        """.trimIndent()) }
        val db = ValidationDB(ds)
        assert(db.check() == listOf("Invalid nullability: table validation_entity (age INTEGER not null)"))
    }
}