package kotgres.annotations


@Target(AnnotationTarget.FUNCTION)
annotation class Query(
    val value: String,
)