### Transactions
* readonly transactions √
* transaction management √

### Validation
* detect name duplications in nested objects
* detect names exceeding 63 symbols length 
* postgres type aliases (text, varchar)
* check types where it is possible
* detect cycles

### Converters
* custom type converters? Most probably it is not needed. Could be solved by using kewt-mapping

### Migrations
* generate migration scripts (flyway)

### Nested objects
* ~~one to one mappings using joined table~~
* one to many mapping using JSON √
* ~~one to many using additional select per collection~~
* Enums

### Querying
* custom queries:
    * in method names (for single parameter) √
        * use parameter names instead? `findBy(id: Int): Iphone`    √
        * use parameter annotations `findBy(@Param("cust_id") id: Int): Iphone`
    * custom `@Where` annotation √
      * use object notation along with column names
    * `@Join` annotation
      ```koltin
      @PostgresRepository
      interface IphoneRepository : Repository<Iphone> {
        
          @Join("owner as o where o.iphone_id = id")
          @Join("neighbour as n where n.id = o.neighbour_id")
          @Where("cap_city = :capacity and n.name = 'vasya'")
          fun f1(capacity: String): Iphone?
      }
      ```
    * `@Query` queries
        * scalar return type
        * arbitrary objects return type
        * tuples return type?
* return types:
    * object √
    * collection √
    * resultSet
    * row mapper
* JSON queries
* pagination

### Naming
* convert db names to snake case √
* ~~include parent name into db name?~~ (could easily exceed 63 symbols restriction)
* naming strategy?

### Annotations
* make `@Table` annotation optional (detect entities by repository type parameter) √
* support javax.persistence? (`@Id`?)
* support Spring

### Mapping declaration
* annotations √
* DSL (have to be parsed (ANTLR?))
    
    ```kotlin
    @PostgresRepository
    interface IphoneRepository : Repository<Iphone> {
        private fun PostgresMappings.mappings() {
            table(Iphone::class, "iphone")
            id(Iphone::id)
            column(Battery::capacity, name = "cap_city", type = PostgresType.TEXT)
        }
    }
    ```                   
  
### Test
* testcontainers in example √

### Documentation
* kdoc 
* kdoc generated code?
* documentation
