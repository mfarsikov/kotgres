package kotgres.kapt.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.github.enjoydambience.kotlinbard.CodeBlockBuilder
import io.github.enjoydambience.kotlinbard.TypeSpecBuilder
import io.github.enjoydambience.kotlinbard.`for`
import io.github.enjoydambience.kotlinbard.`if`
import io.github.enjoydambience.kotlinbard.`while`
import io.github.enjoydambience.kotlinbard.addClass
import io.github.enjoydambience.kotlinbard.addCode
import io.github.enjoydambience.kotlinbard.addFunction
import io.github.enjoydambience.kotlinbard.buildFile
import io.github.enjoydambience.kotlinbard.controlFlow
import io.github.enjoydambience.kotlinbard.nullable
import kotgres.aux.Checker
import kotgres.kapt.model.klass.Klass
import kotgres.kapt.model.klass.Nullability
import kotgres.kapt.model.klass.Type
import kotgres.kapt.model.klass.isJavaPrimitive
import kotgres.kapt.model.klass.jdbcTypeMappingsForPrimitives
import kotgres.kapt.model.repository.ObjectConstructor
import kotgres.kapt.model.repository.QueryMethod
import kotgres.kapt.model.repository.QueryMethodType
import kotgres.kapt.model.repository.QueryParameter
import kotgres.kapt.model.repository.Repo
import kotgres.kapt.parser.KotlinType
import javax.annotation.processing.Generated


fun generateRepository(repo: Repo): FileSpec {
    return buildFile(repo.superKlass.name.pkg, "${repo.superKlass.name.name}Impl.kt") {

        addClass("${repo.superKlass.name.name}Impl") {
            addAnnotation(Generated::class)
            addModifiers(KModifier.INTERNAL)
            addSuperinterface(ClassName(repo.superKlass.name.pkg, repo.superKlass.name.name))
            primaryConstructor(
                PropertySpec.builder("connection", ClassName("java.sql", "Connection"), KModifier.PRIVATE).build()
            )

            generateCheckFunction(repo)
            repo.queryMethods
                .forEach { queryMethod ->
                    when (queryMethod.type) {
                        QueryMethodType.SINGLE -> generateQueryMethod(queryMethod)
                        QueryMethodType.BATCH -> generateBatchQueryMethod(queryMethod)
                    }
                }
        }
    }
}

private fun TypeSpecBuilder.generateQueryMethod(
    queryMethod: QueryMethod,
) {
    addFunction(queryMethod.name) {
        addModifiers(KModifier.OVERRIDE)
        returns(queryMethod.returnType.toTypeName())
        addParameters(queryMethod.queryMethodParameters.map { param ->
            ParameterSpec(
                name = param.name,
                type = param.type.toTypeName()
            )
        })

        addCode {
            if (queryMethod.orderParameterName == null)
                addStatement("val query = %S", queryMethod.query)
            else
                addStatement(
                    "val query = %S.replace(%S, %L.stringify())",
                    queryMethod.query,
                    "%orderBy",
                    queryMethod.orderParameterName
                )

            controlFlow("return connection.prepareStatement(query).use") {

                generateParametersSetBlock(queryMethod.queryParameters, "")

                if (queryMethod.returnType.klass.name != KotlinType.UNIT.qn) {
                    controlFlow("it.executeQuery().use") {
                        if (queryMethod.returnsCollection || queryMethod.pagination != null)
                            generateCollectionExtractor(queryMethod)
                        else
                            generateSingleElementExtractor(queryMethod)
                    }
                } else {
                    when {
                        queryMethod.optimisticallyLocked -> {
                            addStatement("val rows = it.executeUpdate()")
                            `if`("rows != 1") {
                                addStatement(
                                    "throw %M()",
                                    MemberName("kotgres.aux.exception", "OptimisticLockFailException")
                                )
                            }
                        }
                        queryMethod.isStatement -> addStatement("it.execute()")
                        else -> addStatement("it.executeUpdate()")
                    }
                }
            }
        }
    }
}

private fun CodeBlockBuilder.generateCollectionExtractor(queryMethod: QueryMethod) {
    addStatement(
        "val acc = mutableListOf<%T%L>()",
        queryMethod.trueReturnType.klass.toClassName(),
        if (queryMethod.trueReturnType.nullability == Nullability.NULLABLE) "?" else "",
    )
    `while`("it.next()") {
        addStatement("acc +=")
        indent()
        if (queryMethod.returnsScalar) {
            generateScalarExtraction(queryMethod.objectConstructor as ObjectConstructor.Extractor)
        } else {
            generateConstructorCall(queryMethod.objectConstructor!!)
        }
        unindent()
    }
    if (queryMethod.pagination != null) {
        addStatement(
            "Page(%L, acc)",
            queryMethod.pagination.parameterName
        )//TODO use correct parameter name
    } else {
        addStatement("acc")
    }
}

private fun CodeBlockBuilder.generateSingleElementExtractor(
    queryMethod: QueryMethod,
) {
    `if`("it.next()") {
        if (!queryMethod.returnsCollection) {
            `if`("!it.isLast") {
                addStatement(
                    "throw %T(%S)",
                    IllegalStateException::class,
                    "Query has returned more than one element"
                )
            }
        }
        if (queryMethod.returnsScalar) {
            generateScalarExtraction(queryMethod.objectConstructor as ObjectConstructor.Extractor)
        } else {
            generateConstructorCall(queryMethod.objectConstructor!!)
        }
    } `else` {
        if (queryMethod.returnType.nullability == Nullability.NULLABLE)
            addStatement("null")
        else
            addStatement("throw %T()", NoSuchElementException::class)
    }
}

private fun CodeBlockBuilder.generateScalarExtraction(extractor: ObjectConstructor.Extractor) {
    when {
        extractor.isJson -> addStatement(
            "%M.%M(it.getString(1))",
            MemberName("kotlinx.serialization.json", "Json"),
            MemberName("kotlinx.serialization", "decodeFromString"),
        )

        extractor.resultSetGetterName == "Object" -> addStatement(
            "it.getObject(1, %M::class.java)",
            MemberName(extractor.fieldType.pkg, extractor.fieldType.name)
        )

        extractor.isEnum -> {
            if (extractor.isNullable) {
                addStatement(
                    "it.getString(1)?.let { %M.valueOf(it) }",
                    MemberName(extractor.fieldType.pkg, extractor.fieldType.name),
                )
            } else {
                addStatement(
                    "%M.valueOf(it.getString(1))",
                    MemberName(extractor.fieldType.pkg, extractor.fieldType.name),
                )
            }
        }
        else -> addStatement(
            "it.get${extractor.resultSetGetterName}(1)"
        )
    }
}

fun Klass.toClassName() = ClassName(name.pkg, name.name)

fun Type.toTypeName(): TypeName {

    var cn = klass.toClassName()
    if (nullability == Nullability.NULLABLE) cn = cn.nullable

    return if (typeParameters.isNotEmpty()) {
        val paramType = typeParameters.single()
        val paramClassName = paramType.klass.toClassName()
        val paramWithNullability = if (paramType.nullability == Nullability.NULLABLE) {
            paramClassName.nullable
        } else {
            paramClassName
        }
        cn.parameterizedBy(paramWithNullability)
    } else cn
}

private fun CodeBlockBuilder.generateConstructorCall(c: ObjectConstructor, isTop: Boolean = true) {
    when (c) {
        is ObjectConstructor.Constructor -> {
            val fName = c.fieldName?.let { "$it =" } ?: ""
            addStatement("%L %T(", fName, ClassName(c.className.pkg, c.className.name))
            indent()
            c.nestedFields.forEach { generateConstructorCall(it, false) }
            unindent()
            val trailingComma = if (isTop) "" else ","
            addStatement(")$trailingComma")
        }
        is ObjectConstructor.Extractor -> {
            if (c.isJson) {
                addStatement(
                    "%L = %M.%M(it.getString(%S)),",
                    c.fieldName,
                    MemberName("kotlinx.serialization.json", "Json"),
                    MemberName("kotlinx.serialization", "decodeFromString"),
                    c.columnName,
                )
            } else if (c.resultSetGetterName == "Object") {
                addStatement(
                    "%L = it.getObject(%S, %M::class.java),",
                    c.fieldName,
                    c.columnName,
                    MemberName(c.fieldType.pkg, c.fieldType.name)
                )
            } else if (c.isEnum) {
                if (c.isNullable) {
                    addStatement(
                        "%L = it.getString(%S)?.let { %M.valueOf(it) },",
                        c.fieldName,
                        c.columnName,
                        MemberName(c.fieldType.pkg, c.fieldType.name),
                    )
                } else {
                    addStatement(
                        "%L = %M.valueOf(it.getString(%S)),",
                        c.fieldName,
                        MemberName(c.fieldType.pkg, c.fieldType.name),
                        c.columnName,
                    )
                }
            } else if (c.isPrimitive && c.isNullable) {
                addStatement(
                    "%L = it.get${c.resultSetGetterName}(%S).takeIf { _ -> !it.wasNull() },",
                    c.fieldName,
                    c.columnName,
                )
            } else {
                addStatement(
                    "%L = it.get${c.resultSetGetterName}(%S),",
                    c.fieldName,
                    c.columnName,
                )
            }
        }
    }
}

private fun TypeSpecBuilder.generateBatchQueryMethod(queryMethod: QueryMethod) {
    addFunction(queryMethod.name) {
        addModifiers(KModifier.OVERRIDE)

        val queryMethodParameter = queryMethod.queryMethodParameters.single()
        addParameter(
            ParameterSpec(queryMethodParameter.name, queryMethodParameter.type.toTypeName())
        )


        addCode {
            addStatement("val query = %S", queryMethod.query)
            controlFlow("connection.prepareStatement(query).use") {
                `for`("item in ${queryMethodParameter.name}") {

                    generateParametersSetBlock(queryMethod.queryParameters, "item.")

                    addStatement("it.addBatch()")
                }
                if (queryMethod.optimisticallyLocked) {
                    addStatement("val rows = it.executeBatch()")
                    `if`("rows.sum() != ${queryMethodParameter.name}.size") {
                        addStatement(
                            "throw %M()",
                            MemberName("kotgres.aux.exception", "OptimisticLockFailException")
                        )
                    }
                } else {
                    addStatement("it.executeBatch()")
                }
            }
        }
    }
}

private fun CodeBlockBuilder.generateParametersSetBlock(
    queryParameters: List<QueryParameter>,
    itemPrefix: String
) {
    for (param in queryParameters) {
        when {
            param.convertToArray -> addStatement(
                "it.setArray(%L, connection.createArrayOf(%S, $itemPrefix%L.toTypedArray()))",
                param.positionInQuery,
                param.postgresType.value,
                param.path,
            )

            param.isJson -> addStatement(
                """it.setObject(%L, %M().apply { type = "jsonb"; value = %M.%M($itemPrefix%L) })""",
                param.positionInQuery,
                MemberName("org.postgresql.util", "PGobject"),
                MemberName("kotlinx.serialization.json", "Json"),
                MemberName("kotlinx.serialization", "encodeToString"),
                param.path,
            )

            param.isEnum -> addStatement(
                "it.setString(%L, $itemPrefix%L%L.name)",
                param.positionInQuery,
                param.path,
                if (param.kotlinType.nullability == Nullability.NULLABLE) "?" else ""
            )

            param.kotlinType.klass.isJavaPrimitive() && param.kotlinType.nullability == Nullability.NULLABLE ->
                `if`("$itemPrefix%L == null", param.path) {
                    addStatement(
                        "it.setNull(%L, %M.%L)",
                        param.positionInQuery,
                        MemberName("java.sql", "Types"),
                        jdbcTypeMappingsForPrimitives[param.postgresType]
                            ?: error("no java.sql.Types mapping for ${param.postgresType}")
                    )
                } `else` {
                    addStatement(
                        "it.set${param.setterName}(%L, $itemPrefix%L)",
                        param.positionInQuery,
                        param.path,
                    )
                }

            else -> addStatement(
                "it.set${param.setterName}(%L, $itemPrefix%L)",
                param.positionInQuery,
                param.path,
            )
        }
    }
}

private fun TypeSpecBuilder.generateCheckFunction(repo: Repo) {
    if (repo.mappedKlass == null) return

    addFunction("check") {
        addModifiers(KModifier.OVERRIDE)
        returns(List::class.parameterizedBy(String::class))

        addCode {
            val columnMember = ClassName("kotgres.aux", "ColumnDefinition")
            val typeMember = ClassName("kotgres.aux", "PostgresType")

            addStatement("val columns = listOf(")
            indent()
            repo.mappedKlass.columns.mapIndexed { i, it ->
                val separator = if (i != repo.mappedKlass.columns.size - 1) "," else ""
                addStatement(
                    "%T(name = %S, nullable = %L, type = %T.%L, isId = %L )%L",
                    columnMember,
                    it.column.name,
                    it.column.nullable,
                    typeMember,
                    it.column.type,
                    it.column.isId,
                    separator,
                )
            }
            unindent()
            addStatement(")")

            addStatement(
                "return listOfNotNull(%T.check(%S, columns, connection))",
                Checker::class,
                repo.mappedKlass.name,
            )
        }
    }
}

fun TypeSpec.Builder.primaryConstructor(vararg properties: PropertySpec): TypeSpec.Builder {
    val propertySpecs = properties.map { p -> p.toBuilder().initializer(p.name).build() }
    val parameters = propertySpecs.map { ParameterSpec.builder(it.name, it.type).build() }
    val constructor = FunSpec.constructorBuilder()
        .addParameters(parameters)
        .build()

    return this
        .primaryConstructor(constructor)
        .addProperties(propertySpecs)
}
