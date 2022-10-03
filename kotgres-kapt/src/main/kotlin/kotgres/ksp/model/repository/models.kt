package kotgres.ksp.model.repository

import kotgres.aux.PostgresType
import kotgres.ksp.common.Pagination
import kotgres.ksp.model.db.TableMapping
import kotgres.ksp.model.klass.Klass
import kotgres.ksp.model.klass.QualifiedName
import kotgres.ksp.model.klass.Type
import kotgres.ksp.parser.KotlinType

data class Repo(
    val superKlass: Klass,
    val queryMethods: List<QueryMethod>,
    val mappedKlass: TableMapping?,
    val belongsToDb: QualifiedName,
)

data class QueryMethod(
    val name: String,
    val query: String,
    val queryMethodParameters: List<QueryMethodParameter>,
    val queryParameters: List<QueryParameter>,
    val returnType: Type, // TODO remove?
    val trueReturnType: Type,
    val returnsCollection: Boolean,
    val objectConstructor: ObjectConstructor?,
    val returnsScalar: Boolean = false,
    val pagination: Pagination?,
    val type: QueryMethodType = QueryMethodType.SINGLE,
    val orderParameterName: String? = null,
    val optimisticallyLocked: Boolean,
    val isStatement: Boolean = false,
)

enum class QueryMethodType { SINGLE, BATCH }

data class QueryMethodParameter(
    val name: String,
    val type: Type,
)

data class QueryParameter(
    val positionInQuery: Int,
    val kotlinType: Type,
    val setterName: String,
    val path: String,
    val isJson: Boolean,
    val isEnum: Boolean,
    val convertToArray: Boolean,
    val isINClause: Boolean,
    val postgresType: PostgresType,
)

sealed class ObjectConstructor {
    data class Constructor(
        val fieldName: String?,
        val className: QualifiedName,
        val nestedFields: List<ObjectConstructor>,
    ) : ObjectConstructor()

    data class Extractor(
        val resultSetGetterName: String, // TODO remove in favor of kotlinType?
        val columnName: String,
        val fieldName: String?,
        val fieldType: QualifiedName, // TODO remove in favor of kotlinType?
        val isJson: Boolean,
        val isEnum: Boolean,
        val isPrimitive: Boolean,
        val isNullable: Boolean,
        val kotlinType: KotlinType?,
    ) : ObjectConstructor()
}
