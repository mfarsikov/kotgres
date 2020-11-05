package postgres.json.model.repository

import postgres.json.model.db.TableMapping
import postgres.json.model.klass.Klass
import postgres.json.model.klass.QualifiedName
import postgres.json.model.klass.Type

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
    ) : ObjectConstructor()
}
