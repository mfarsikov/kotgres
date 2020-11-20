package my.pack

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ValidationTest {

    val db = ValidationDB(TestUtil.ds)
    @AfterEach
    fun cleanup(){
        TestUtil.ds.connection.use { it.createStatement().execute("""
            drop table validation_entity
        """.trimIndent()) }
    }

    @Test
    fun `happy path`(){
        TestUtil.ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid primary key,
                name text not null,
                age integer
            )
        """.trimIndent()) }
        assert(db.check() == emptyList<String>())
    }

    @Test
    fun `missing primary key`(){
        TestUtil.ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid not null,
                name text not null,
                age integer
            )
        """.trimIndent()) }
        assert(db.check() == listOf("Missing keys: table validation_entity (id UUID not null)"))
    }

    @Test
    fun `extra primary key`(){
        TestUtil.ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid,
                name text,
                age integer,
                primary key (id, name)
            )
        """.trimIndent()) }
        assert(db.check() == listOf("Extra keys: table validation_entity (name TEXT not null)"))
    }

    @Test
    fun `missing column`(){
        TestUtil.ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid primary key,
                age integer
            )
        """.trimIndent()) }
        assert(db.check() == listOf("Missing columns: table validation_entity (name TEXT not null)"))
    }

    @Test
    fun `extra column`(){
        TestUtil.ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid primary key,
                name text not null,
                age integer,
                extra int
            )
        """.trimIndent()) }

        assert(db.check() == listOf("Extra columns: table validation_entity (extra INTEGER)"))
    }

    @Test
    fun `invalid nullable column`(){
        TestUtil.ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid primary key,
                name text,
                age integer
            )
        """.trimIndent()) }
        assert(db.check() == listOf("Invalid nullability: table validation_entity (name TEXT)")) //TODO "table: nullable, entity: non nullable"
    }

    @Test
    fun `invalid not nullable column`(){
        TestUtil.ds.connection.use { it.createStatement().execute("""
            create table validation_entity (
                id uuid primary key,
                name text not null,
                age integer not null
            )
        """.trimIndent()) }
        assert(db.check() == listOf("Invalid nullability: table validation_entity (age INTEGER not null)"))
    }
}