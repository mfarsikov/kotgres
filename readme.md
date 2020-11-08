# Kotgres

See `example` directory

`./gradlew example:kaptKotlin` generates database classes in `example/build/generated/source/kapt/main`

`./gradlew example:test` runs real queries against DB in docker container (requires Docker)

`./gradlew example:run` runs Main application in `example` project, requires running Postgres.

Type mappings:

|Kotlin type              | Postgresql type             |
|-------------------------|-----------------------------|
|java.math.BigDecimal     | numeric                     |
|kotlin.Boolean           | boolean                     |
|kotlin.ByteArray         | bytea                       |
|java.sql.Date            | date                        |
|kotlin.Double            | double precision            |
|kotlin.Float             | real                        |
|kotlin.Int               | integer                     |
|kotlin.collections.List  | jsonb                       |
|kotlin.Long              | bigint                      |
|java.time.LocalDate      | date                        |
|java.time.LocalDateTime  | timestamp without time zone |
|java.time.LocalTime      | time without time zone      |
|kotlin.collections.Map   | jsonb                       |
|kotlin.String            | text                        |
|java.sql.Time            | time without time zone      |
|java.sql.Timestamp       | timestamp with time zone    |
|java.util.UUID           | uuid                        |

