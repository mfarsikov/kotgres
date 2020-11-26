package my.pack

import kotgres.annotations.PostgresRepository
import kotgres.annotations.Query

@PostgresRepository
interface StandaloneRepository {
    @Query(
    """
       SELECT id, date, list FROM my_class WHERE name = :name 
    """
    )
    fun selectProjectionByName(name: String): ProjectionOfMyClass
}