package kotgres.model.db

import kotgres.model.klass.Klass
import kotgres.model.klass.Type
import kotgres.model.repository.ObjectConstructor

data class TableMapping(
    val name: String,
    val klass: Klass,
    val columns: List<ColumnMapping>,
    val objectConstructor: ObjectConstructor,
)

data class ColumnMapping(
    val path: List<String>,
    val type: Type,
    val column: ColumnDefinition,
)


data class ColumnDefinition(
    val name: String,
    val nullable: Boolean,
    val type: PostgresType,
    val isId: Boolean = false,
){
    override fun toString() = "$name ${type.value} ${if (!nullable) "not null" else "" }"
}

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
    TIME("time without time zone"), // with timezone not recomended
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
