package kotgres.ksp.common

import kotgres.ksp.model.klass.KlassFunction
import kotgres.ksp.model.klass.QualifiedName

data class Pagination(
    val parameterName: String,
)

val pageableQualifiedName = QualifiedName("kotgres.aux.page", "Pageable")
val pageQualifiedName = QualifiedName("kotgres.aux.page", "Page")

fun KlassFunction.paginationParameter(): Pagination? {
    val paginationParameter = parameters.firstOrNull { it.type.klass.name == pageableQualifiedName }
    if ((returnType.klass.name == pageQualifiedName) xor (paginationParameter != null)) {
        throw KotgresException("Function $name must have both Pageable parameter and Page return type")
    }
    return paginationParameter?.let { Pagination(it.name) }
}
