package kotgres.kapt.model.repository

import kotgres.kapt.model.db.TableMapping
import kotgres.kapt.model.klass.Klass
import kotgres.kapt.model.klass.QualifiedName
import kotgres.kapt.model.klass.Type

data class Repo(
    val superKlass: Klass,
    val queryMethods: List<QueryMethod>,
    val saveAllMethod: QueryMethod,
    val findAllMethod: QueryMethod,
    val deleteAllMethod: QueryMethod,
    val mappedKlass: TableMapping, //TODO probably only constructor is used. check and remove
)

data class QueryMethod(
    val name: String,
    val query: String,
    val queryParameters: List<QueryParameter>,
    val returnType: Type, //TODO remove?
    val returnKlass: Klass,
    val returnsCollection: Boolean,
    val objectConstructor: ObjectConstructor?,
    val returnsScalar: Boolean = false,
)

data class QueryParameter(
    val position: Int,
    val type: Type,
    val setterType: String,
    val path: String,
    val isJson: Boolean,
    val isEnum: Boolean,
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
    ) : ObjectConstructor()
}
