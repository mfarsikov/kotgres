# Kotgres

1. [Conventions](#conventions) 
2. [Standalone repositories](#standalone-repositories)
3. [Dedicated repositories](#dedicated-repositories)
4. [Database object](#database-object)
5. [Transactions](#transactions)

## Conventions

Table and column names are the class and property names converted to `lower_snake_case`

Table name can be changed using annotation `@Table(name = "my_table")` on class level.
Column name can be changed using `@Column(name = "my_column")` on property level.
```kotlin
@Table(name = "person")
data class Person(
  @Id
  @Column(name = "id")
  val id: UUID,
  @Column(name = "name")
  val name: String,
  @Column(name = "birth_date")
  val birthDate: LocalDate
)
```

Kotgres generates queries based on method names in two cases:
* method name starts with `save`
* method name starts with `delete`

Other names have no sense for it, and are treated as queries.

This name convention could be overriden by annotations:
```kotlin
@Save
fun add(items: List<Item>)

@Delete
fun remove(id: UUID)

@Query
fun deletedItems(deleted: Boolean = true): List<Item>
```

## Standalone repositories
Standalone is a repository without any connected entity. It has no insights on table name and columns structure, 
so all the queries should be written manually. The only thing that can be generated - is transforming tuples from result 
set to requested entities.

The simplest possible standalone interface is this:
```kotlin
@PostgresRepository
interface PersonRepository 
```
It is useles, but Kotgres can generate implementation for it.
For each interface marked as `@PostgresRepository` task `kaptKotlin` generates implementations in  
`build/generated/source/kapt/` folder.
<details>
<summary>Generated code</summary>

```kotlin
@Generated
internal class PersonRepositoryImpl(
  private val connection: Connection
) : PersonRepository 
```
</details>

### Simplest query

Next we can add a function, and since Kotgres does not have enough information to generate a SQL query 
each method in standalone repository should have manually written query:
```kotlin
@PostgresRepository
interface PersonRepository {
  @Query("SELECT id, name, birth_date FROM person")
  fun findPeople(): List<Person>
}
```
Method name does not make sense, all information is taken from annotation, input parameter types (or absence of input parameters)
and the return type.
Kotgres knows that return type is a `List` so it expects multiple results.
Based on list type parameter `Person` it also knows which fields to extract from the `ResultSet`.

<details>
<summary>Generated code</summary>

```kotlin
public override fun findPeople(): List<Person> {
  val query = "SELECT id, name, birth_date FROM person"
  return connection.prepareStatement(query).use {
    it.executeQuery().use {
      val acc = mutableListOf<Person>()
      while (it.next()) {
        acc +=
          Person(
            birthDate = it.getObject("birth_date", LocalDate::class.java),
            id = it.getObject("id", java.util.UUID::class.java),
            name = it.getString("name"),
          )
      }
      acc
    }
  }
}
```
</details>

### Query parameters

Method parameters could be passed to a query:
```kotlin
@Query("SELECT id, name, birth_date FROM person WHERE name = :name")
fun selectWhere(name: String): List<Person>
```
Note that the parameter name (`name`) must match the named placeholder in the query (`:name`).
Query parameters are set to the query, and it is safe in terms of SQL injections.

<details>
<summary>Generated code</summary>

```kotlin
  public override fun selectWhere(name: String): List<Person> {
  val query = "SELECT id, name, birth_date FROM person WHERE name = ?"
  return connection.prepareStatement(query).use {
    it.setString(1, name)
    it.executeQuery().use {
      val acc = mutableListOf<Person>()
      while (it.next()) {
        acc +=
          Person(
            birthDate = it.getObject("birth_date", LocalDate::class.java),
            id = it.getObject("id", UUID::class.java),
            name = it.getString("name"),
          )
      }
      acc
    }
  }
}
```
</details>

### Return single non-nullable value

If method returns a single non-nullable value
```kotlin
@Query("SELECT id, name, birth_date FROM person WHERE id = :id")
fun selectWhere(id: UUID): Person
```
Generated code will throw exceptions in two cases:
* if query does not have any result
* if query has more than one result

<details>
<summary>Generated code</summary>

```kotlin
public override fun selectWhere(id: java.util.UUID): Person {
  val query = """
      |SELECT id, name, birth_date FROM person WHERE id = ?
      |LIMIT 2
      """.trimMargin()
  return connection.prepareStatement(query).use {
    it.setObject(1, id)
    it.executeQuery().use {
      if (it.next()) {
        if (!it.isLast) {
          throw IllegalStateException("Query has returned more than one element")
        }
         Person(
          birthDate = it.getObject("birth_date", LocalDate::class.java),
          id = it.getObject("id", UUID::class.java),
          name = it.getString("name"),
        )
      }
      else {
        throw NoSuchElementException()
      }
    }
  }
}
```
</details>

### Return single nullable value

If return type is nullable:
```kotlin
@Query("SELECT id, name, birth_date FROM person WHERE id = :id")
fun selectWhere(id: UUID): Person?
```
Generated code returns `null` if there is no result
<details>
<summary>Generated code</summary>

```kotlin
public override fun selectWhere(id: java.util.UUID): Person? {
  val query = """
      |SELECT id, name, birth_date FROM person WHERE id = ?
      |LIMIT 2
      """.trimMargin()
  return connection.prepareStatement(query).use {
    it.setObject(1, id)
    it.executeQuery().use {
      if (it.next()) {
        if (!it.isLast) {
          throw IllegalStateException("Query has returned more than one element")
        }
         Person(
          birthDate = it.getObject("birth_date", LocalDate::class.java),
          id = it.getObject("id", UUID::class.java),
          name = it.getString("name"),
        )
      }
      else {
        null
      }
    }
  }
}
```
</details>

### Alternative to `in` operator

```kotlin
@Query("SELECT id, name, birth_date FROM person WHERE name = ANY :names")
fun selectWhere(names: List<String>): List<Person>
```


<details>
<summary>Generated code</summary>

```kotlin
public override fun selectWhere(names: List<String>): List<Person> {
  val query = "SELECT id, name, birth_date FROM person WHERE name = ANY ?"
  return connection.prepareStatement(query).use {
    it.setArray(1, connection.createArrayOf("text", names.toTypedArray()))
    it.executeQuery().use {
      val acc = mutableListOf<Person>()
      while (it.next()) {
        acc +=
           Person(
            birthDate = it.getObject("birth_date", LocalDate::class.java),
            id = it.getObject("id", UUID::class.java),
            name = it.getString("name"),
          )
      }
      acc
    }
  }
}
```
</details>
  
### Pagination

```kotlin
@Query("SELECT id, name, birth_date FROM person WHERE name = :name")
fun select(name: String, pagination: Pageable): Page<Person>
```

<details>
<summary>Generated code</summary>

```kotlin
public override fun select(name: String, pagination: Pageable): Page<Person> {
  val query = """
      |SELECT id, name, birth_date FROM person WHERE name = ?
      |LIMIT ? OFFSET ?
      """.trimMargin()
  return connection.prepareStatement(query).use {
    it.setString(1, name)
    it.setInt(2, pagination.pageSize)
    it.setInt(3, pagination.offset)
    it.executeQuery().use {
      val acc = mutableListOf<Person>()
      while (it.next()) {
        acc +=
           Person(
            birthDate = it.getObject("birth_date", LocalDate::class.java),
            id = it.getObject("id", UUID::class.java),
            name = it.getString("name"),
          )
      }
      Page(pagination, acc)
    }
  }
}
```
</details>

### Scalar return type

```kotlin
@Query("SELECT name WHERE id = :id")
fun selectNameWhere(id: UUID): String
```
<details>
<summary>Generated code</summary>

```kotlin
public override fun selectNameWhere(id: java.util.UUID): String {
  val query = """
      |SELECT name WHERE id = ?
      |LIMIT 2
      """.trimMargin()
  return connection.prepareStatement(query).use {
    it.setObject(1, id)
    it.executeQuery().use {
      if (it.next()) {
        if (!it.isLast) {
          throw IllegalStateException("Query has returned more than one element")
        }
        it.getString(1)
      }
      else {
        throw NoSuchElementException()
      }
    }
  }
}
```
</details>

### List of scalar return type

```kotlin
@Query("SELECT id WHERE name = :name")
fun selectIdsWhere(name: String): List<UUID>
```
<details>
<summary>Generated code</summary>

```kotlin
public override fun selectIdsWhere(name: String): List<java.util.UUID> {
  val query = "SELECT id WHERE name = ?"
  return connection.prepareStatement(query).use {
    it.setString(1, name)
    it.executeQuery().use {
      val acc = mutableListOf<java.util.UUID>()
      while (it.next()) {
        acc +=
          it.getObject(1, UUID::class.java)
      }
      acc
    }
  }
}
```
</details>

### Updates
Any update or delete must have a `Unit` return type
```kotlin
@Query("UPDATE person SET name = :name WHERE id = :id")
fun update(id: UUID, name: String)
```
<details>
<summary>Generated code</summary>

```kotlin
public override fun update(id: UUID, name: String): Unit {
  val query = "UPDATE person SET name = ? WHERE id = ?"
  return connection.prepareStatement(query).use {
      it.setString(1, name)
      it.setObject(2, id)
      it.executeUpdate()
  }
}
```
</details>

### Statements
Any statement must have a `Unit` return type
```kotlin
@Statement("SELECT set_config('log_statement', 'all', true)")
fun turnOnLogsOnServerForCurrentTransaction()
```
<details>
<summary>Generated code</summary>

```kotlin
public override fun turnOnLogsOnServerForCurrentTransaction(): Unit {
  val query = "SELECT set_config('log_statement', 'all', true)"
  return connection.prepareStatement(query).use {
    it.execute()
  }
}
```
</details>

## Dedicated repositories 

Standalone repositories can not do a lot, because they do not know enough context, unlike dedicated repositories, 
which are attached to specific entity (table).


### Entity

Entity is a Kotlin data class. 
It should be declared in the source code, not imported from a library (maybe this will be changed in future).
Entity should have property types listed in [type mappings](#type-mappings).
There is no required annotations to declare an entity, simplest declaration could be:
```kotlin
data class Person(
  val id: UUID,
  val name: String,
  val birthDate: LocalDate,
)
```
This means that it is attached to table `person` with columns `id`, `name` and `birth_date`.

### @Id

If entity has an ID it must be marked with `@Id` annnotation:
```kotlin
data class Person(
  @Id
  val id: UUID,
  val name: String,
  val birthDate: LocalDate,
)
```
Entity with ID additionally has:
* `ON CONFLICT DO UPDATE` in save query (save works like a merge rather than an insert)
* primary key existence check on DB validation

Entity can have more than one `@Id` field

### Repository declaration
Each dedicated repository interface must be annotated with `@PostgresRepository` and extend `Repository`

### Query methods
# TODO
#### Method name
In cases of querying method a method name does not matter and could be anything.
For both cases will be generated the same code:
```kotlin
fun findByFirstName(firstName: String): List<Person>
fun select(firstName: String): List<Person>
```
The generated query will be something like: `SELECT first_name, last_name FROM person WHERE first_name = ?`. 
The `first_name = ?` is 'inferred' based on the parameter name, so Parameter names should match entity's property names.

#### Return type
Return type could be one of:
* Entity (throws an exception if there is no result or if there is more than one result)
* Nullable entity (throws if there is more than one result)
* List of entities

```kotlin
fun select(firstName: String): List<Person>
fun findById(id: UUID): Person?
fun findByLicenseNumber(licenseNumber: String): Person
```

#### @Limit and @First
If method return type is list, it can be annotated with `@Limit`:
```kotlin
@Limit(10)
fun findByName(name: String): List<Person>
```
If method declares entity or scalar, but query returns more than one element - it throws an exception.
To change this behavior `@First` annotation could be used:
```kotlin
@First
fun findByBirthDate(birthDate: LocalDate): Person
```
Note that method that returns a List cannot be annotated with `@First`, 
as well as method that returns an entity or scalar cannot be annotated with `@Limit` 
#### Projections
Besides entities query methods can return projections. For example for entity
```kotlin
data class Person(val firstName: String, val lastName: String, val age: Int)
```
projection could be any data class having some of Entity's fields:
```kotlin
data class PersonProjection1(val firstName: String, val lastName: String)
data class PersonProjection2(val age: Int)
```
and generated code will query only those required fields
```kotlin
fun findByFirstName(firstName: String): List<PersonProjection1>
fun select(id: UUID): PersonProjection2?
```
#### Complex conditions using @Where 
If method has more than one parameter, they are combined using `AND` logic.
Parameters compared using equality checks only.
In case if more sophisticated logic is required `@Where` annotation should be used:
```kotlin
@Where("first_name like :namePattern OR birth_date <= :birthDate")
fun select(namePattern: String, birthDate: LocalDate): List<Person>
```

#### Custom @Query methods
User can define any custom query, which is mapped to any data class. In this case column names in result set should match 
projection class field names (up to camelCase to snake_case conversion) 
```kotlin
@Query("""
    SELECT p.first_name, p.last_name, d.age
    FROM person AS p
    JOIN documents AS d ON p.id = d.person_id
    WHERE p.first_name like :namePattern
""")
fun select(namePattern: String): PersonProjection 
```
Also, custom query methods can have scalar ("primitive") or list of scalars as a return type:
```kotlin
@Query("SELECT birth_date FROM person WHERE id = :id")
fun selectBirthDate(id: UUID): LocalDate?
@Query("SELECT birth_date FROM person")
fun selectAllBirthDates(): List<LocalDate>
@Query("SELECT count(*) FROM person")
fun selectPersonNumber(): Int
```
`@Query` annotation cannot be combined with none of: `@Where`, `@Limit`, `@First`. 
It should contain the whole query

### Delete methods
Same as find methods, except: it returns nothing, and it's name should start from a `delete` word.
```kotlin
fun delete(id: UUID)
```


## Database object
Database object gives access to transactions DSL and contains all the generated repositories.

```kotlin
val db = DB(dataSource)

val johns = db.transaction {
    // the only way to obtain a repository is to start a transaction
    personRepository.selectAllWhere(lastName = "John")
}
```
It's fully qualified name is configured in `build.gradle.kts`:
```kotlin
kapt {
  arguments {
    arg("kotgres.db.qualifiedName", "my.pack.DB") 
  }
}
```
By default, all repositories are assigned to this database object, unless other is specified in 
`@PostgresRepository` annotation:
```kotlin
@PostgresRepository(belongsToDb = "my.another.DbObject")
interface MyRepository : Repository<MyEntity>
```

### Spring support
DB objects could be marked as Spring components `build.gradle.kts`:

```kotlin
kapt {
  arguments {
    arg("kotgres.spring", "true")
  }
}
```
generated class:
```kotlin
import org.springframework.stereotype.Component

@Generated
@Component
public class DB(
  private val ds: DataSource
) {
...
```
So it could be instantiated and further injected by Spring.

## Transactions
Any repository interactions are done inside a transaction. 
This does not introduce any overhead, since even if you do not declare transaction explicitly, it is started implicitly.

### Transaction DSL
Inside transaction lambda all DB's repositories are available through `this`:
```kotlin
val people = db.transaction {
    this.personRepository.findAll()
}
``` 
Of cource `this` can be skipped:
```kotlin
val people = db.transaction {
    personRepository.findAll()
}
``` 
If lambda completed successfully - transaction is committed.
Any exception thrown from the lambda rolls back the transaction.
Also, transaction can be rolled back manually:
```kotlin
db.transaction {
    personRepository.saveAll(people)
    if (somethingGoneWrong) rollback()
}
``` 
It is possible to rollback to certain save point:
```kotlin
db.transaction {
    personRepository.saveAll(people)
    val savePoint = savePoint()
      ...
    if (somethingGoneWrong) rollbackTo(savePoint)
}
``` 
If transaction is read only, it could be specified:
```kotlin
val people = db.transaction(readOnly = true) {
    personRepository.findAll()
}
```

Default isolation level (READ_COMMITTED) can be changed per transaction:
```kotlin
db.transaction(isolationLevel = IsolationLevel.SERIALIZABLE) {
    ...
}
```

## Database verification
```kotlin
DB(dataSource).check()
```
Checks all underlying repositories and returns list of errors or empty list if everything is ok.

Checks for absent/extra fields, type/nullability mismatch, key fields/primary keys.

## Type mappings

| Kotlin type               |  Postgresql type              |
| ------------------------- | ----------------------------- |
| java.math.BigDecimal      |  numeric                      |
| kotlin.Boolean            |  boolean                      |
| kotlin.ByteArray          |  bytea                        |
| java.sql.Date             |  date                         |
| kotlin.Double             |  double precision             |
| kotlin.Float              |  real                         |
| kotlin.Int                |  integer                      |
| kotlin.collections.List   |  jsonb                        |
| kotlin.Long               |  bigint                       |
| java.time.LocalDate       |  date                         |
| java.time.LocalDateTime   |  timestamp without time zone  |
| java.time.LocalTime       |  time without time zone       |
| kotlin.collections.Map    |  jsonb                        |
| kotlin.String             |  text                         |
| java.sql.Time             |  time without time zone       |
| java.sql.Timestamp        |  timestamp with time zone     |
| java.util.UUID            |  uuid                         |
