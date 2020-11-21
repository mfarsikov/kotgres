package kotgres.annotations

/**
 * Adds LIMIT 1 to query which returns non collection type
 * Returns first matched element
 */
@Target(AnnotationTarget.FUNCTION)
annotation class First
