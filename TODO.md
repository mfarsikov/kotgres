### Validation
* detect name duplications in nested objects
* detect names exceeding 63 symbols length 
* postgres type aliases (text, varchar)
* check types where it is possible
* detect cycles
* primary key exists (`@Id`) in DB
* nested objects are not null

### Converters
* custom type converters? Most probably it is not needed. Could be solved by using kewt-mapping
* Instant to Timestamp with time zone

### Migrations
* generate migration scripts (flyway)
  * gradle plugin? using test containers? embed in tests?

### Querying
* JSON queries
* pagination

### Naming
* naming strategy?

### Documentation
* kdoc 
* kdoc generated code?
* documentation


### nullable
* enums
* nested classes
* jsonb