### Validation
* detect name duplications in nested objects
* detect names exceeding 63 symbols length 
* postgres type aliases (text, varchar)
* check types where it is possible
* detect cycles
* nested objects are not null

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
*`hasPrevious` `hasNext`?

### Queries
* do not use specific logic for findAll method
* Rework @Limit, and allow to use it as a query parameter