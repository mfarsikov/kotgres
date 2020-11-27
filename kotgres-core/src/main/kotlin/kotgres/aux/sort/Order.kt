package kotgres.aux.sort

data class Order(
    val by: List<SortCol>
) {
    fun stringify(): String {
        val invalid = by.filterNot { it.isValid() }

        if (invalid.isNotEmpty()) throw Exception("Invalid field names: $invalid") //TODO use custom exception

        if (by.isEmpty()) return ""

        fun SortOrder?.stringify(): String = when (this) {
            null -> ""
            SortOrder.ASC -> " ASC"
            SortOrder.DESC -> " DESC"
        }

        fun NullsOrder?.stringify(): String = when (this) {
            null -> ""
            NullsOrder.NULLS_LAST -> " NULLS LAST"
            NullsOrder.NULLS_FIRST -> " NULLS FIRST"
        }

        val by = by.joinToString { it.name + it.sortOrder.stringify() + it.nullsOrder.stringify() }

        return "ORDER BY $by"
    }
}