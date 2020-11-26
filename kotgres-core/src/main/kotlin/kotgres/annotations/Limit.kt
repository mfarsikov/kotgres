package kotgres.annotations

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class Limit(
    val value: Int = Int.MAX_VALUE
)
