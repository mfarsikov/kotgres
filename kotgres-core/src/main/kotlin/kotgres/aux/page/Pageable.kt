package kotgres.aux.page

import kotgres.aux.sort.SortCol

data class Page<T>(//TODO check it works from a jar, not from sources
    val pageable: Pageable,
    val content: List<T>,
)

data class Pageable(
    val pageNumber: Int,
    val pageSize: Int,
    val sort: List<SortCol> = emptyList(),
){
    val offset = pageNumber * pageSize
}