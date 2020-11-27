package my.pack

import kotgres.annotations.Id
import kotgres.annotations.PostgresRepository
import kotgres.annotations.Version
import kotgres.aux.Repository
import java.util.*

data class OptimisticallyLockedItem(
    @Id
    val id: UUID,

    @Version
    val version: Int = 0,
)

@PostgresRepository
interface OptimisticLockRepository : Repository<OptimisticallyLockedItem> {
    fun save(item: OptimisticallyLockedItem)
    fun saveAll(items: List<OptimisticallyLockedItem>)
    fun find(id: UUID): OptimisticallyLockedItem?
    fun deleteAll()
    fun delete(item: OptimisticallyLockedItem)
}