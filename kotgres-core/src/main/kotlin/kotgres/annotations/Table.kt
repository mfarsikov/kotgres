package kotgres.annotations

@Target(AnnotationTarget.CLASS)
annotation class Table(
    val name: String = ""
)

