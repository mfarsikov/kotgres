package kotgres.aux


enum class PostgresType(
    val value: String
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
    NUMERIC("numeric"), //TODO has params
    TIME("time without time zone"),
    TIMESTAMP("timestamp without time zone"),
    TIMESTAMP_WITH_TIMEZONE("timestamp with time zone"),
    XML("xml"),
    TSVECTOR("tsvector"),
    UUID("uuid"),
    ;

    companion object {
        fun of(s: String): PostgresType {
            return values().singleOrNull { it.value == s } ?: error("Cannot find PostgresType: $s")
        }
    }
}