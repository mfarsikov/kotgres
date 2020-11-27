package kotgres.aux

data class ColumnDefinition(
    val name: String,
    val nullable: Boolean,
    val type: PostgresType,
    val isId: Boolean,
    val isVersion: Boolean = false,
){
    override fun toString() = "$name ${type}${if (!nullable) " not null" else "" }"
}