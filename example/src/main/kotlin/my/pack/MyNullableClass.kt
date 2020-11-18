package my.pack

import kotgres.annotations.Id
import kotgres.annotations.PostgresRepository
import kotgres.annotations.Query
import kotgres.annotations.Where
import kotgres.aux.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

data class MyNullableClass(
    @Id
    val id: String,
    val name: String?,
    val myNestedClass: MyNullableNestedClass,
    val version: Int?,
    val bool: Boolean?,
    val date: Date?,
    val timestamp: Timestamp?,
    val uuid: UUID?,
    val time: LocalTime?,
    val localDate: LocalDate?,
    val localDateTime: LocalDateTime?,
    val list: List<String>,
    val enum: Mode,
)


data class MyNullableNestedClass(
    val proc: String?,
    val myNestedNestedClass: MyNullableNestedNestedClass,
)

data class MyNullableNestedNestedClass(
    val capacity: String,
    val longivity: String,
)

data class NullableProjectionOfMyClass(val id: String?, val date: Date?, val list: List<String>)

@PostgresRepository(belongsToDb = "my.pack.NullableDb")
interface MyNullableClassRepository : Repository<MyNullableClass> {

    fun findById(id: String): MyNullableClass?
    fun findByName(name: String?): MyNullableClass?
    fun findByDate(date: Date): List<MyNullableClass>
    fun findByIdOrThrow(id: String): MyNullableClass
    fun findBySpecProc(proc: String): List<MyNullableClass>
    fun findSingleBySpecProc(proc: String): MyNullableClass
    fun findByIdAndVersion(id: String, version: Int): MyNullableClass?
    fun findByTimestamp(timestamp: Timestamp): List<MyNullableClass>
    fun findByUUID(uuid: UUID): MyNullableClass?
    fun findByTime(time: LocalTime): List<MyNullableClass>
    fun findByLocalDate(localDate: LocalDate): List<MyNullableClass>
    fun findByLocalDateTime(localDateTime: LocalDateTime): List<MyNullableClass>
    fun findByMode(enum: Mode): List<MyNullableClass>

    fun findByIdIn(id: List<String>): List<MyNullableClass>

    fun delete(id: String, date: Date)
    fun deleteByDate(date: Date)

    @Where("capacity = :capacity and date <= :date and :v <= version")
    fun findByCapacityAndVersion(capacity: String, v: Int, date: Date): List<MyNullableClass>

    fun selectProjection(proc: String): NullableProjectionOfMyClass?

    @Query("select id, date, list from my_nullable_class where proc = :proc")
    fun selectProjectionCustomQuery(proc: String): NullableProjectionOfMyClass?

    @Where("proc = :proc")
    fun selectProjectionWhere(proc: String): NullableProjectionOfMyClass?

    @Where("id = ANY (:id)")
    fun selectProjectionWhere(id: List<String>): List<NullableProjectionOfMyClass>

    @Query("select date from my_nullable_class where id = :id")
    fun selectDate(id: String): Date?

    @Query("select date from my_nullable_class where proc = :proc")
    fun selectDates(proc: String): List<Date>

    @Query("update my_nullable_class set date = :date where id = :id")
    fun update(id: String, date: Date)

    @Query("select id, date, list from my_nullable_class where date = ANY (:date)")
    fun customSelectWhereDatesIn(date: List<Date>): List<NullableProjectionOfMyClass>
}
