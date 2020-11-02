package my.pack

import postgres.json.lib.Column
import postgres.json.lib.Id
import postgres.json.lib.PostgresRepository
import postgres.json.lib.Repository
import postgres.json.lib.Table
import postgres.json.lib.Where
import postgres.json.lib.toDate
import postgres.json.model.db.PostgresType
import java.sql.Date
import java.time.LocalDate
import kotlin.reflect.KFunction

@Table
data class Iphone(
    @Id
    val id: String,
    val name: String,
    val spec: Spec,
    val version: Int,
    val bool: Boolean,
    @Column(name = "date", PostgresType.DATE, toSqlFunction = "postgres.json.lib.toDate", fromSqlFunction = "postgres.json.lib.toLocalDate")
    val date: LocalDate,
)

data class Spec(
    val proc: String,
    val battery: Battery,
)

data class Battery(
    @Column(name = "cap_city", type = PostgresType.TEXT)
    val capacity: String,
    val longivity: String,
)

@PostgresRepository
interface IphoneRepository : Repository<Iphone> {

    fun findById(id: String): Iphone?
    fun findByIdOrThrow(id: String): Iphone
    fun findBySpecProc(proc: String): List<Iphone>
    fun findSingleBySpecProc(proc: String): Iphone
    fun findByIdAndVersion(id: String, version: Int): Iphone?

    fun delete(id: String)

    @Where("cap_city = :capacity and version >= :v")
    fun findByCapacityAndVersion(capacity: String, v: Int): List<Iphone>

}
