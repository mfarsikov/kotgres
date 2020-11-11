### Validation
* detect name duplications in nested objects
* detect names exceeding 63 symbols length 
* postgres type aliases (text, varchar)
* check types where it is possible
* detect cycles
* primary key exists (`@Id`) in DB

### Converters
* custom type converters? Most probably it is not needed. Could be solved by using kewt-mapping
* Instant to Timestamp with time zone

### Migrations
* generate migration scripts (flyway)
  * gradle plugin? using test containers? embed in tests?

### Querying
* custom queries:
    * IS NULL, setNull
* JSON queries
* pagination

### Naming
* naming strategy?

### Annotations
* support Spring

### Mapping declaration
* Composite keys

### Documentation
* kdoc 
* kdoc generated code?
* documentation
