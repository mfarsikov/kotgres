package kotgres.kapt.model.klass

import javax.lang.model.element.Element

data class QualifiedName(
    val pkg: String,
    val name: String,
) {
    override fun toString(): String =
        "${pkg.takeIf { it.isNotEmpty() }?.let { "$it." } ?: ""}$name"
}

data class Klass(
    val name: QualifiedName,
    val fields: List<Field> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
    val isInterface: Boolean = false,
    val functions: List<KlassFunction> = emptyList(),
    val element: Element? = null,
    val superclassParameter: Type? = null,
    val isEnum: Boolean = false,
)

data class Field(
    val name: String,
    val type: Type,
    val annotations: List<Annotation> = emptyList(),
)

data class Type(
    val klass: Klass,
    val nullability: Nullability = Nullability.NON_NULLABLE,
    val typeParameters: List<Type> = emptyList()
) {
    override fun toString() = "${qualifiedName()}${params()}${nullableSign()}"

    fun qualifiedName(): String = klass.name.toString()

    private fun nullableSign() = when (nullability) {
        Nullability.NULLABLE -> "?"
        Nullability.NON_NULLABLE -> ""
    }

    private fun params() =
        typeParameters.takeIf { it.isNotEmpty() }?.joinToString(prefix = "<", postfix = ">") ?: ""
}

data class KlassFunction(
    val name: String,
    val parameters: List<FunctionParameter>,
    val returnType: Type,
    val annotationConfigs: List<Annotation>,
    val abstract: Boolean,
    val isExtension: Boolean,
) {
    override fun toString() = "$name(${parameters.map { it }.joinToString()}): ${returnType}"
}

data class FunctionSignature(
    val functionName: String,
    val parameters: List<QualifiedName>
)

data class FunctionParameter(
    val name: String,
    val type: Type,
    val isTarget: Boolean,
    val annotations: List<Annotation>,
)

enum class Nullability {
    NULLABLE {
        override fun toString() = "?"
    },
    NON_NULLABLE {
        override fun toString() = ""
    }
}

