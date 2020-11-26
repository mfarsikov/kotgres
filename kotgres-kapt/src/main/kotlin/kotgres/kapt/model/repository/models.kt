package kotgres.kapt.model.repository

import kotgres.aux.PostgresType
import kotgres.kapt.mapper.Pagination
import kotgres.kapt.model.db.TableMapping
import kotgres.kapt.model.klass.Klass
import kotgres.kapt.model.klass.QualifiedName
import kotgres.kapt.model.klass.Type

data class Repo(
    val superKlass: Klass,
    val queryMethods: List<QueryMethod>,
    val mappedKlass: TableMapping, //TODO probably only constructor is used. check and remove
    val belongsToDb: QualifiedName,
)

data class QueryMethod(
    val name: String,
    val query: String,
    val queryMethodParameters: List<QueryMethodParameter>,
    val queryParameters: List<QueryParameter>,
    val returnType: Type, //TODO remove?
    val trueReturnType: Type,
    val returnsCollection: Boolean,
    val objectConstructor: ObjectConstructor?,
    val returnsScalar: Boolean = false,
    val pagination: Pagination?,
    val type: QueryMethodType = QueryMethodType.SINGLE,
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
    val postgresType: PostgresType,
)

sealed class ObjectConstructor {
    data class Constructor(
        val fieldName: String?,
        val className: QualifiedName,
        val nestedFields: List<ObjectConstructor>,
    ) : ObjectConstructor()

    data class Extractor(
        val resultSetGetterName: String,
        val columnName: String,
        val fieldName: String?,
        val fieldType: QualifiedName,
        val isJson: Boolean,
        val isEnum: Boolean,
        val isPrimitive: Boolean,
        val isNullable: Boolean,
    ) : ObjectConstructor()
}
