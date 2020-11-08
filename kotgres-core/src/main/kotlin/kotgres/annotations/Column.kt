package kotgres.annotations

import kotgres.aux.PostgresType

@Target(AnnotationTarget.FIELD)
annotation class Column(
    val name: String = "",
    val type: PostgresType = PostgresType.NONE,
)