package kotgres.ksp.model.klass

sealed class Node
data class QualifiedName(
    val pkg: String,
    val name: String,
) {
    override fun toString(): String =
        "${pkg.takeIf { it.isNotEmpty() }?.let { "$it." } ?: ""}$name"
}

data class Klass(
    val name: QualifiedName,
    var fields: List<Field> = emptyList(),
    var annotations: List<AAnnotation> = emptyList(),
    val isInterface: Boolean = false,
    var functions: List<KlassFunction> = emptyList(),
    var superclassParameter: Type? = null,
    val isEnum: Boolean = false,
) : Node()

data class Field(
    val name: String,
    val type: Type,
    val annotations: List<AAnnotation> = emptyList(),
) : Node()

data class Type(
    val klass: Klass,
    val nullability: Nullability = Nullability.NON_NULLABLE,
    val typeParameters: List<Type> = emptyList(),
) : Node() {
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
    val annotationConfigs: List<AAnnotation>,
    val abstract: Boolean,
    val isExtension: Boolean,
) : Node() {
    override fun toString() = "$name(${parameters.map { it }.joinToString()}): $returnType"
}

data class AAnnotation(
    val name: String,
    val parameters: Map<String, String>,
) : Node()

data class FunctionSignature(
    val functionName: String,
    val parameters: List<QualifiedName>,
)

data class FunctionParameter(
    val name: String,
    val type: Type,
    val isTarget: Boolean,
    val annotations: List<AAnnotation>,
) : Node() {
    override fun toString(): String {
        return "$name: $type"
    }
}

enum class Nullability {
    NULLABLE {
        override fun toString() = "?"
    },
    NON_NULLABLE {
        override fun toString() = ""
    }
}
