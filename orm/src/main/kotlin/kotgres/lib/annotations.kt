package kotgres.lib

import kotgres.model.db.PostgresType

@Target(AnnotationTarget.FIELD)
annotation class Id

@Target(AnnotationTarget.CLASS)
annotation class Table(
    val name: String = ""
)

@Target(AnnotationTarget.FIELD)
annotation class Column(
    val name: String = "",
    val type: PostgresType = PostgresType.NONE,
)

@Target(AnnotationTarget.CLASS)
annotation class PostgresRepository(
    /**
     * Can be either simple class name or fully qualified class name.
     * In case if it is a simple class name, a package name should be configured in TODO
     */
    val belongsToDb: String = ""
)

@Target(AnnotationTarget.FUNCTION)
annotation class Where(
    val value: String,
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class Join(
    val value: String,
)

@Target(AnnotationTarget.FUNCTION)
annotation class Query(
    val value: String,
)


interface Repository<T> : Checkable {
    fun save(item: T)
    fun saveAll(items: List<T>)
    fun findAll(): List<T>
    fun deleteAll()
}

interface Checkable {
    fun check(): List<String>
}