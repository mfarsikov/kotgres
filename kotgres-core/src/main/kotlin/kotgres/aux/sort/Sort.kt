package kotgres.aux.sort


private val allowedNameRegex = """([a-zA-Z_0-9]+)|("[a-zA-Z_0-9 ]+")""".toRegex()

data class SortCol(
    val name: String,
    val sortOrder: SortOrder? = null,
    val nullsOrder: NullsOrder? = null,
) {
    fun isValid() = name matches allowedNameRegex
}

enum class NullsOrder { NULLS_FIRST, NULLS_LAST }
enum class SortOrder { ASC, DESC }