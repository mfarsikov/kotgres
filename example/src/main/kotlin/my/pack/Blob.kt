package my.pack

import kotgres.annotations.PostgresRepository
import kotgres.aux.Repository

data class Blob(
    val byteArray: ByteArray,
)

@PostgresRepository(belongsToDb = "my.pack.BlobDb")
interface BlobRepository : Repository<Blob> {
    fun findAll(): List<Blob>
    fun deleteAll()
    fun saveAll(items: List<Blob>)
    fun save(item: Blob)
}
