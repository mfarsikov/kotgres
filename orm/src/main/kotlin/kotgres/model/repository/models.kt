package kotgres.model.repository

import kotgres.model.db.TableMapping
import kotgres.model.klass.Klass
import kotgres.model.klass.QualifiedName
import kotgres.model.klass.Type

data class Repo(
    val superKlass: Klass,
    val queryMethods: List<QueryMethod>,
    val saveAllMethod: QueryMethod,
    val findAllMethod: QueryMethod,
    val deleteAllMethod: QueryMethod,
    val mappedKlass: TableMapping,
)

data class QueryMethod(
    val name: String,
    val query: String,
    val queryParameters: List<QueryParameter>,
    val returnType: Type,
    val returnsCollection: Boolean,
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
