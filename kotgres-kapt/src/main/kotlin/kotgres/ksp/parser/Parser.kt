package kotgres.ksp.parser

import kotgres.ksp.model.klass.QualifiedName

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
    return KotlinType.of(qualifiedName)?.qn ?: qualifiedName
}

enum class KotlinType(val qn: QualifiedName, val jdbcSetterName: String?) {
    BIG_DECIMAL(QualifiedName(pkg = "java.math", name = "BigDecimal"), "BigDecimal"),
    BOOLEAN(QualifiedName(pkg = "kotlin", name = "Boolean"), "Boolean"),
    BYTE_ARRAY(QualifiedName(pkg = "kotlin", name = "ByteArray"), "Bytes"),
    DATE(QualifiedName(pkg = "java.sql", name = "Date"), "Date"),
    DOUBLE(QualifiedName(pkg = "kotlin", name = "Double"), "Double"),
    FLOAT(QualifiedName(pkg = "kotlin", name = "Float"), "Float"),

    // INSTANT(QualifiedName(pkg = "java.time", name = "Instant"), "Object"),
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

        // TODO fill
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
