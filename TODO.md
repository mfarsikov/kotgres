### Validation
* detect name duplications in nested objects
* detect names exceeding 63 symbols length 
* postgres type aliases (text, varchar)
* check types where it is possible
* detect cycles
* nested objects are not null
* print traceable errors (with class name and function 'coordinates')

### Converters
* custom type converters? Most probably it is not needed. Could be solved by using kewt-mapping
* Instant to Timestamp with time zone

### Migrations
* generate migration scripts (flyway)
  * gradle plugin? using test containers? embed in tests?

### Documentation
* kdoc 
* kdoc generated code?
* documentation

throw on insert duplicate
### Pagination
* `totalNumber` in paged query?
  * alternative could be another query
* `hasPrevious` (if page > 0 then has previous?) 
* `hasNext` (limit + 1?)

### Queries
* Do not use specific logic for `findAll` method (`ObjectTableMapper.kt`)
* Rework `@Limit`, and allow to use it as a query parameter
* Add `@Order` annotation
* Support dynamic ordering (Sort as a query parameter) (combined with limit?)
* Add `count()` method to repository
  * if repo query returns Int is it count?
  ```kotlin
  @Where("name like :pattern")
  fun select(pattern: String): Int
  ```
* Repository could consist of custom queries, and it is not required to extend the `Repository<T>`
  * error on query methods without `@Query` annotation
* Optimistic lock
* Delete by entity `fun delete(item: Item)`
