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

### Pagination
* `totalNumber` in paged query?
  * alternative could be another query
* `hasPrevious` (if page > 0 then has previous?) 
* `hasNext` (limit + 1?)

### Queries
* Find by all fields?
```kotlin
fun find(item: Item): Item?
fun delete(item: Item)
fun exists(item: Item): Boolean
```
* delete by list
```kotlin
fun delete(items: List<Item>)
```

* Batch insert sends whole query per item. Check whether it is possible to use syntax 
  (probably with additional annotation, and without ON CONFLICT):
```sql
insert into t (col_a, col_b)
values
(1, 2),
(3, 4),
...
```