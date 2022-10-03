package kotgres.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSClassifierReference
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import kotgres.ksp.model.klass.AAnnotation
import kotgres.ksp.model.klass.Field
import kotgres.ksp.model.klass.FunctionParameter
import kotgres.ksp.model.klass.Klass
import kotgres.ksp.model.klass.KlassFunction
import kotgres.ksp.model.klass.Node
import kotgres.ksp.model.klass.QualifiedName
import kotgres.ksp.model.klass.Type
import kotgres.ksp.parser.KotlinType

class KotgresSymbolVisitor(val logger: KSPLogger) : KSDefaultVisitor<Unit, Node?>() {
    val cache = mutableMapOf<QualifiedName, Klass>()

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit): Node {
        logger.info("Parsing class: ${classDeclaration.qualifiedName!!.asString()}")
        println("Parsing class: ${classDeclaration.qualifiedName!!.asString()}")
        val qualifiedName = QualifiedName(
            pkg = classDeclaration.qualifiedName?.getQualifier() ?: "",
            name = classDeclaration.qualifiedName?.getShortName()!!,
        )

        val cached = cache[qualifiedName]
        if (cached != null) return cached

        val kotlinType = KotlinType.of(qualifiedName)?.let { Klass(name = it.qn) }
        if (kotlinType != null) {
            cache[qualifiedName] = kotlinType
            return kotlinType
        }

        val cls = Klass(
            name = qualifiedName,
            isInterface = classDeclaration.classKind == ClassKind.INTERFACE,
            isEnum = classDeclaration.classKind == ClassKind.ENUM_CLASS,
        )
        cache[qualifiedName] = cls
        if (cls.isEnum) return cls

        cls.superclassParameter =
            classDeclaration.superTypes.firstOrNull()?.element?.typeArguments?.firstOrNull()?.type?.accept(
            this,
            data,
        ) as Type?
        cls.fields = classDeclaration.getDeclaredProperties().toList().map { it.accept(this, data) as Field }
        cls.annotations = classDeclaration.annotations.toList().map { it.accept(this, data) as AAnnotation }
        cls.functions =
            classDeclaration.getDeclaredFunctions().toList().mapNotNull { it.accept(this, data) as KlassFunction? }

        logger.info("Parsed class: $qualifiedName")
        return cls
    }

    override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit): Node {
        logger.info("Parsed property: ${property.qualifiedName?.asString()}")
        val resolve = property.type.resolve()
        return Field(
            name = property.qualifiedName?.getShortName()!!,
            type = Type(
                klass = resolve.declaration.accept(this, data) as Klass,
                nullability = n(resolve),
            ).also {
                logger.info("Parsed field: $it")
            },
            annotations = property.annotations.toList().map { it.accept(this, data) as AAnnotation },
        )
    }

    private fun n(t: KSType): kotgres.ksp.model.klass.Nullability {
        return when (t.nullability) {
            Nullability.NULLABLE -> kotgres.ksp.model.klass.Nullability.NULLABLE
            Nullability.NOT_NULL -> kotgres.ksp.model.klass.Nullability.NON_NULLABLE
            else -> throw Exception("platform type is not supported")
        }
    }

    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit): KlassFunction? {
        if (function.isConstructor()) return null
        logger.info("Parsed function: ${function.simpleName.asString()}")

        return KlassFunction(
            name = function.simpleName.getShortName(),
            parameters = function.parameters.map { it.accept(this, data) as FunctionParameter },
            returnType = function.returnType?.accept(this, data) as Type,
            annotationConfigs = function.annotations.map { it.accept(this, data) as AAnnotation }.toList(),
            abstract = function.isAbstract,
            isExtension = function.extensionReceiver != null,
        ).also {
            logger.info("Parsed function: $it")
        }
    }

    override fun visitTypeReference(typeReference: KSTypeReference, data: Unit): Node {
        val resolve = typeReference.resolve()
        logger.info("Parsed type reference: ${resolve.declaration.qualifiedName?.asString()}")
        return Type(
            klass = resolve.declaration.accept(this, data) as Klass,
            nullability = n(resolve),
            typeParameters = typeReference.element?.typeArguments?.map { it.accept(this, data) as Type } ?: emptyList(),
        ).also {
            logger.info("Parsed typeRef: $it")
        }
    }

    override fun visitAnnotation(annotation: KSAnnotation, data: Unit): Node {
        logger.info("Parsed annotation: ${annotation.shortName}")
        return AAnnotation(
            name = annotation.annotationType.resolve().declaration.qualifiedName!!.asString(),
            parameters = annotation.arguments.associate { (it.name?.getShortName() ?: "value") to it.value.toString() },
        ).also {
            logger.info("Parsed Annotation: $it")
        }
    }

    override fun visitClassifierReference(reference: KSClassifierReference, data: Unit): Node? {
        logger.info("refName: ${reference.referencedName()},args: ${reference.typeArguments.joinToString()}")
        return null
    }

    override fun visitTypeArgument(typeArgument: KSTypeArgument, data: Unit): Node? {
        logger.info("Parsing typeArgument: ${typeArgument.type?.resolve()}")
        return Type(
            klass = typeArgument.type?.resolve()?.declaration?.accept(this, data) as Klass,
            nullability = n(typeArgument.type!!.resolve()),
            typeParameters = emptyList(),
        ).also {
            logger.info("Parsed typeArgument: $it")
        }
    }

    override fun visitTypeParameter(typeParameter: KSTypeParameter, data: Unit): Klass {
        logger.info("Parsing type parameter: ${typeParameter.name.asString()}")
        return typeParameter.parentDeclaration!!.accept(this, data) as Klass
    }

    override fun visitValueParameter(valueParameter: KSValueParameter, data: Unit): Node? {
        logger.info("parsing values parameter ${valueParameter.name?.asString()}")

        return FunctionParameter(
            name = valueParameter.name!!.asString(),
            type = valueParameter.type.accept(this, data) as Type,
            isTarget = false, // TODO
            annotations = valueParameter.annotations.map { it.accept(this, data) as AAnnotation }.toList(),
        )
    }

    override fun defaultHandler(node: KSNode, data: Unit): Node? {
        logger.error("Encountered unexcpected node: ${node::class.qualifiedName}")
        return null
    }
}
