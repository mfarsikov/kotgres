package kotgres.aux

enum class PostgresType(
    val value: String,
) {
    NONE(""),
    BOOLEAN("boolean"),
    BIGINT("bigint"),
    INTEGER("integer"),
    TEXT("text"),
    JSONB("jsonb"),
    BYTEA("bytea"),
    REAL("real"),
    DATE("date"),
    DOUBLE("double precision"),
    MONEY("money"),
    NUMERIC("numeric"), // TODO has params
    TIME("time without time zone"),
    TIMESTAMP("timestamp without time zone"),
    TIMESTAMP_WITH_TIMEZONE("timestamp with time zone"),
    XML("xml"),
    TSVECTOR("tsvector"),
    UUID("uuid"),
    ;

    companion object {
        fun of(s: String): PostgresType {
            // TODO inconsistent values in 's': sometimes it is FQN, and sometimes value. should be fixed
            return if (s.startsWith(PostgresType::class.qualifiedName!!)) {
                val name = s.substringAfterLast(".")
                values().singleOrNull { it.name == name } ?: error("Cannot find PostgresType: $s")
            } else {
                val name = s.lowercase()
                values().singleOrNull { it.value == name } ?: error("Cannot find PostgresType: $s")
            }
        }
    }
}
