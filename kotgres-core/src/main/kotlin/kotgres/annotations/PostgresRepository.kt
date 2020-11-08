package kotgres.annotations

@Target(AnnotationTarget.CLASS)
annotation class PostgresRepository(
    /**
     * Can be either simple class name or fully qualified class name.
     * In case if it is a simple class name, a package name should be configured in TODO
     */
    val belongsToDb: String = ""
)