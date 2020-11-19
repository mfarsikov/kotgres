package my.pack

import kotgres.annotations.Id
import kotgres.annotations.PostgresRepository
import kotgres.aux.Repository
import java.util.*

data class ValidationEntity(
    @Id
    val id: UUID,
    val name: String,
    val age: Int?,
)

@PostgresRepository(belongsToDb = "my.pack.ValidationDB")
interface ValidationRepo : Repository<ValidationEntity>
