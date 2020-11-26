package kotgres.kapt.parser

import com.sun.tools.javac.code.Symbol
import kotgres.annotations.Column
import kotgres.annotations.First
import kotgres.annotations.Id
import kotgres.annotations.Limit
import kotgres.annotations.OnConflictFail
import kotgres.annotations.PostgresRepository
import kotgres.annotations.Query
import kotgres.annotations.Table
import kotgres.annotations.Where
import kotgres.kapt.model.klass.Field
import kotgres.kapt.model.klass.FunctionParameter
import kotgres.kapt.model.klass.FunctionSignature
import kotgres.kapt.model.klass.Klass
import kotgres.kapt.model.klass.KlassFunction
import kotgres.kapt.model.klass.Nullability
import kotgres.kapt.model.klass.QualifiedName
import kotgres.kapt.model.klass.Type
import kotlinx.metadata.Flag
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmType
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind

class Parser(
    roundEnv: RoundEnvironment,
    private val processingEnv: ProcessingEnvironment,
) {
    private val elementsByName: Map<QualifiedName, Element> = roundEnv.rootElements
        .associateBy {
            QualifiedName(
                pkg = processingEnv.elementUtils.getPackageOf(it).qualifiedName.toString(),
                name = it.simpleName.toString()
            )
        }

    private val cache = mutableMapOf<QualifiedName, Klass>()

    fun parse(element: Element): Klass {
        element as Symbol.ClassSymbol
        return find(element.className().toQualifiedName())
    }

    private fun parseInternal(element: Element): Klass {

        return when (val kotlinClassMetadata = readMetadata(element.getAnnotation(Metadata::class.java))) {
            is KotlinClassMetadata.Class -> parseClass(element, kotlinClassMetadata, processingEnv)
            else -> error("Unexpected element $kotlinClassMetadata")
        }
    }

    private fun parseClass(
        element: Element,
        metadata: KotlinClassMetadata.Class,
        processingEnvironment: ProcessingEnvironment
    ): Klass {

        val kmClass = metadata.toKmClass()

        element as Symbol.ClassSymbol

        if (element.getKind() == ElementKind.ENUM) {
            return Klass(
                name = QualifiedName(
                    pkg = processingEnvironment.elementUtils.getPackageOf(element).qualifiedName.toString(),
                    name = kmClass.name.substringAfterLast("/")
                ),
                isEnum = true,
            )
        }

        val superclassParam = kmClass.supertypes.firstOrNull()?.let { superclass ->
            superclass.arguments.firstOrNull().let { param -> param?.type?.toType() }
        }

        val functionSignatureToAnnotations: Map<FunctionSignature, List<Annotation>> = element.members()
            .elements
            .map { it as Element }
            .filterIsInstance<Symbol.MethodSymbol>()
            .associate {
                it.toFunctionSignature() to listOfNotNull(
                    it.getAnnotation(Where::class.java),
                    it.getAnnotation(Query::class.java),
                    it.getAnnotation(Limit::class.java),
                    it.getAnnotation(First::class.java),
                    it.getAnnotation(OnConflictFail::class.java),
                )
            }

        val functions = kmClass.functions.asSequence()
            .filterNot { Flag.Function.IS_SYNTHESIZED.invoke(it.flags) }
            .map { func ->
                KlassFunction(
                    name = func.name,
                    parameters = func.valueParameters.map { param ->
                        FunctionParameter(
                            name = param.name,
                            type = param.type!!.toType(),
                            isTarget = false,
                            annotations = emptyList()
                        )
                    },
                    returnType = func.returnType.toType(),
                    annotationConfigs = functionSignatureToAnnotations[func.toFunctionSignature()] ?: emptyList(),
                    abstract = false,
                    isExtension = false
                )
            }
            .toList()

        val tableAnnotation: Table? = element.getAnnotation(Table::class.java)
        val repoAnnotation: PostgresRepository? = element.getAnnotation(PostgresRepository::class.java)

        val fieldNameToAnnotationDetails = element.members_field.elements
            .filterIsInstance<Symbol.VarSymbol>()
            .associate {
                it.name.toString() to listOfNotNull(
                    it.getAnnotation(Column::class.java),
                    it.getAnnotation(Id::class.java)
                )
            }

        return Klass(
            element = element,
            name = QualifiedName(
                pkg = processingEnvironment.elementUtils.getPackageOf(element).qualifiedName.toString(),
                name = kmClass.name.substringAfterLast("/")
            ),
            fields = kmClass.properties.map {
                Field(
                    name = it.name,
                    type = it.returnType.toType(),
                    annotations = fieldNameToAnnotationDetails[it.name] ?: emptyList()
                )
            },
            annotations = listOfNotNull(tableAnnotation, repoAnnotation),
            functions = functions,
            isInterface = element.isInterface,
            superclassParameter = superclassParam,
        )
    }

    private fun find(qn: QualifiedName): Klass {
        return cache.computeIfAbsent(qn) { simpleType ->
            when (simpleType) {
                !in elementsByName -> Klass(name = qn)
                else -> parseInternal(elementsByName[simpleType]!!)
            }
        }
    }

    private fun KmType.toType(): Type {
        val classifier = classifier as KmClassifier.Class
        val qualifiedName = classifier.name.replace("/", ".").toQualifiedName()
        val typeArguments = arguments.mapNotNull { it.type?.toType() }
        val isNullable = Flag.Type.IS_NULLABLE.invoke(this.flags)
        return Type(
            klass = find(qualifiedName),
            nullability = if (isNullable) Nullability.NULLABLE else Nullability.NON_NULLABLE,
            typeParameters = typeArguments
        )
    }

    private fun Symbol.MethodSymbol.toFunctionSignature() = FunctionSignature(
        functionName = name.toString(),
        parameters = params.map { (it.type.tsym as Symbol.ClassSymbol).className().toQualifiedName() }
    )

    private fun KmFunction.toFunctionSignature() = FunctionSignature(
        functionName = name,
        parameters = valueParameters.map {
            it.type!!.toType().klass.name
        }
    )
}

private fun readMetadata(metadata: Metadata): KotlinClassMetadata? = metadata.let {
    KotlinClassHeader(
        it.kind,
        it.metadataVersion,
        it.bytecodeVersion,
        it.data1,
        it.data2,
        it.extraString,
        it.packageName,
        it.extraInt
    )
}
    .let { KotlinClassMetadata.read(it) }


/**
 * com.my.company.MyClass<in kotlin.Int, out kotlin.String>
 *     group 2: com.my.company
 *     group 3: MyClass
 *     group 4: <in kotlin.Int, out kotlin.String>
 */
private val typeDeclarationPattern = "^(([\\w\\.]*)\\.)?(\\w*)(<.*>)?".toRegex()
fun String.toQualifiedName(): QualifiedName {
    val groups = typeDeclarationPattern.find(this)!!.groups
    val qualifiedName = QualifiedName(groups[2]?.value ?: "", groups[3]?.value!!)
    return KotlinType.of(qualifiedName)?.qn?:qualifiedName
}

enum class KotlinType(val qn: QualifiedName, val jdbcSetterName: String?) {
    BIG_DECIMAL(QualifiedName(pkg = "java.math", name = "BigDecimal"), "BigDecimal"),
    BOOLEAN(QualifiedName(pkg = "kotlin", name = "Boolean"), "Boolean"),
    BYTE_ARRAY(QualifiedName(pkg = "kotlin", name = "ByteArray"), "Bytes"),
    DATE(QualifiedName(pkg = "java.sql", name = "Date"), "Date"),
    DOUBLE(QualifiedName(pkg = "kotlin", name = "Double"), "Double"),
    FLOAT(QualifiedName(pkg = "kotlin", name = "Float"), "Float"),
    //INSTANT(QualifiedName(pkg = "java.time", name = "Instant"), "Object"),
    INT(QualifiedName(pkg = "kotlin", name = "Int"), "Int"),
    LIST(QualifiedName(pkg = "kotlin.collections", name = "List"), "Object"),
    LONG(QualifiedName(pkg = "kotlin", name = "Long"), "Long"),
    LOCAL_DATE(QualifiedName(pkg = "java.time", name = "LocalDate"), "Object"),
    LOCAL_DATE_TIME(QualifiedName(pkg = "java.time", name = "LocalDateTime"), "Object"),
    LOCAL_TIME(QualifiedName(pkg = "java.time", name = "LocalTime"), "Object"),
    MAP(QualifiedName(pkg = "kotlin.collections", name = "Map"), "Object"),
    STRING(QualifiedName(pkg = "kotlin", name = "String"), "String"),
    TIME(QualifiedName(pkg = "java.sql", name = "Time"), "Time"),
    TIMESTAMP(QualifiedName(pkg = "java.sql", name = "Timestamp"), "Timestamp"),
    UNIT(QualifiedName(pkg = "kotlin", name = "Unit"), null),
    UUID(QualifiedName(pkg = "java.util", name = "UUID"), "Object"),
    ;

    companion object {
        fun of(qualifiedName: QualifiedName): KotlinType? {
            return values().singleOrNull { it.qn == qualifiedName } ?: synonyms[qualifiedName]
        }

        //TODO fill
        private val synonyms: Map<QualifiedName, KotlinType> = mapOf(
            QualifiedName(pkg = "", name = "int") to INT,
            QualifiedName(pkg = "", name = "long") to LONG,
            QualifiedName(pkg = "", name = "float") to FLOAT,
            QualifiedName(pkg = "", name = "double") to DOUBLE,
            QualifiedName(pkg = "", name = "boolean") to BOOLEAN,
            QualifiedName(pkg = "java.lang", name = "Integer") to INT,
            QualifiedName(pkg = "java.lang", name = "Long") to LONG,
            QualifiedName(pkg = "java.lang", name = "Float") to FLOAT,
            QualifiedName(pkg = "java.lang", name = "Double") to DOUBLE,
            QualifiedName(pkg = "java.lang", name = "Boolean") to BOOLEAN,
            QualifiedName(pkg = "java.lang", name = "String") to STRING,
            QualifiedName(pkg = "java.util", name = "List") to LIST,

        )
    }
}
