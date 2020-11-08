package kotgres.aux

interface Repository<T> : Checkable {
    fun save(item: T)
    fun saveAll(items: List<T>)
    fun findAll(): List<T>
    fun deleteAll()
}