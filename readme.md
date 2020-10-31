# Kotgres

See `example` directory

`./gradlew example:kaptKotlin` generates database classes in `example/build/generated/source/kapt/main`

`./gradlew example:test` runs real queries against DB in docker container (requires Docker)

`./gradlew example:run` runs Main application in `example` project, requires running Postgres.
