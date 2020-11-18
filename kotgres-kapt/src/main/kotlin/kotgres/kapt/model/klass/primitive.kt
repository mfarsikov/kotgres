package kotgres.kapt.model.klass

import kotgres.aux.PostgresType

fun Klass.isJavaPrimitive() = name in primitives

val primitives = listOf(
    QualifiedName("kotlin","Int" ),
    QualifiedName("kotlin","Long" ),
    QualifiedName("kotlin","Float" ),
    QualifiedName("kotlin","Double" ),
    QualifiedName("kotlin","Boolean" ),
)

val jdbcTypeMappingsForPrimitives = mapOf(
    PostgresType.INTEGER to "INTEGER",
    PostgresType.BIGINT to "BIGINT",
    PostgresType.REAL to "FLOAT",
    PostgresType.DOUBLE to "DOUBLE",
    PostgresType.BOOLEAN to "BOOLEAN",
)