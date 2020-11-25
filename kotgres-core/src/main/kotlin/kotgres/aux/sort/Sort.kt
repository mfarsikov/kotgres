package kotgres.aux.sort

data class SortCol(
    val name: String,
    val sortOrder: SortOrder = SortOrder.ASC,
    val nullsOrder: NullsOrder = NullsOrder.NULLS_LAST
)

enum class NullsOrder { NULLS_FIRST, NULLS_LAST }
enum class SortOrder { ASC, DESC }