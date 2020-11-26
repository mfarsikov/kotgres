package kotgres.aux.page

import kotgres.aux.sort.SortCol

data class Page<T>(//TODO check it works from a jar, not from sources
    val pageable: Pageable,
    val content: List<T>,
    val sort: List<SortCol> = emptyList(),
)

data class Pageable(
    val pageNumber: Int,
    val pageSize: Int,
){
    val offset = pageNumber * pageSize
}