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
import kotgres.kapt.model.klass.QualifiedName
import kotgres.kapt.model.klass.Type
import kotgres.kapt.model.repository.ObjectConstructor
import kotgres.kapt.model.repository.QueryMethod
import kotgres.kapt.model.repository.Repo
import kotgres.kapt.parser.KotlinType
import javax.annotation.Generated


fun generateRepository(repo: Repo): FileSpec {
    return buildFile(repo.superKlass.name.pkg, "${repo.superKlass.name.name}Impl.kt") {

        addClass("${repo.superKlass.name.name}Impl") {
            addAnnotation(Generated::class)
            addModifiers(KModifier.INTERNAL)
            addSuperinterface(ClassName(repo.superKlass.name.pkg, repo.superKlass.name.name))
            primaryConstructor(
                PropertySpec.builder("connection", ClassName("java.sql", "Connection"), KModifier.PRIVATE).build()
            )

            generateSaveAllFunction(repo.saveAllMethod, repo.mappedKlass.klass)
            generateFindAllFunction(repo.findAllMethod)
            generateCheckFunction(repo)
            generateSaveFunction(repo.saveAllMethod, repo.mappedKlass.klass)
            generateDeleteAllFunction(repo)
            repo.queryMethods.forEach { queryMethod ->
                generateCustomSelectFunction(queryMethod)
            }
        }
    }
}

private fun TypeSpecBuilder.generateCustomSelectFunction(
    queryMethod: QueryMethod,
) {
    addFunction(queryMethod.name) {
        addModifiers(KModifier.OVERRIDE)
        returns(queryMethod.returnType.toTypeName())
        addParameters(queryMethod.queryParameters.sortedBy { it.positionInFunction }.map { param ->
            ParameterSpec(
                name = param.path,
                type = param.type.toTypeName()
            )
        })

        addCode {
            addStatement("val query = %S", queryMethod.query)
            controlFlow("return connection.prepareStatement(query).use") {
                queryMethod.queryParameters.sortedBy { it.positionInQuery }.forEachIndexed { i, param ->
                    if (param.isEnum) {
                        addStatement("it.setString(%L, %L.name)", i + 1, param.path)
                    } else {
                        addStatement("it.set%L(%L, %L)", param.setterType, i + 1, param.path)
                    }
                }
                if (queryMethod.returnType.klass.name != KotlinType.UNIT.qn) {
                    controlFlow("it.executeQuery().use") {
                        if (queryMethod.returnsCollection)
                            generateCollectionExtractor(queryMethod)
                        else
                            generateSingleElementExtractor(queryMethod)
                    }
                } else {
                    addStatement("it.execute()")
                }
            }
        }
    }
}

private fun CodeBlockBuilder.generateCollectionExtractor(queryMethod: QueryMethod) {
    addStatement("val acc = mutableListOf<%T>()", queryMethod.returnKlass.toClassName())
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
    addStatement("acc")
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

        extractor.isEnum -> addStatement(
            "%M.valueOf(it.getString(1))",
            MemberName(extractor.fieldType.pkg, extractor.fieldType.name),
        )

        else -> addStatement(
            "it.get${extractor.resultSetGetterName}(1)"
        )
    }
}

fun Klass.toClassName() = ClassName(name.pkg, name.name)

fun Type.toTypeName(): TypeName {

    var cn = klass.toClassName()
    if (nullability == Nullability.NULLABLE) cn = cn.nullable

    return if (typeParameters.isNotEmpty())
        cn.parameterizedBy(ClassName(typeParameters.single().klass.name.pkg, typeParameters.single().klass.name.name))
    else cn
}

private fun TypeSpecBuilder.generateSaveFunction(saveAllMethod: QueryMethod, klass: Klass) {
    addFunction("save") {
        addModifiers(KModifier.OVERRIDE)
        addParameter(ParameterSpec("item", klass.name.let { ClassName(it.pkg, it.name) }))
        addStatement("saveAll(listOf(item))")
    }
}

private fun TypeSpecBuilder.generateFindAllFunction(findAllMethod: QueryMethod) {
    addFunction("findAll") {
        addModifiers(KModifier.OVERRIDE)
        returns(listParametrizedBy(findAllMethod.returnKlass.name))

        val entityType = findAllMethod.returnType.typeParameters.single()

        addCode {
            addStatement("val query = %S", findAllMethod.query)

            val entityClassName = ClassName(entityType.klass.name.pkg, entityType.klass.name.name)
            controlFlow("return connection.prepareStatement(query).use") {
                controlFlow("it.executeQuery().use") {
                    addStatement("val acc = mutableListOf<%T>()", entityClassName)
                    `while`("it.next()") {
                        addStatement("acc += ")

                        generateConstructorCall(findAllMethod.objectConstructor!!)

                    }
                    addStatement("acc")
                }
            }
        }
    }
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
                addStatement(
                    "%L = %M.valueOf(it.getString(%S)),",
                    c.fieldName,
                    MemberName(c.fieldType.pkg, c.fieldType.name),
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

private fun TypeSpecBuilder.generateSaveAllFunction(saveAllMethod: QueryMethod, klass: Klass) {
    addFunction("saveAll") {
        addModifiers(KModifier.OVERRIDE)

        addParameter(
            ParameterSpec(
                "items", listParametrizedBy(klass.name)
            )
        )
        addCode {
            addStatement("val query = %S", saveAllMethod.query)
            controlFlow("connection.prepareStatement(query).use") {
                `for`("item in items") {
                    for (param in saveAllMethod.queryParameters) {
                        if (param.isJson) {
                            addStatement(
                                """it.setObject(%L, %M().apply { type = "jsonb"; value = %M.%M(item.%L) })""",
                                param.positionInQuery,
                                MemberName("org.postgresql.util", "PGobject"),
                                MemberName("kotlinx.serialization.json", "Json"),
                                MemberName("kotlinx.serialization", "encodeToString"),
                                param.path,
                            )
                        } else if (param.isEnum) {
                            addStatement(
                                "it.setString(%L, item.%L.name)",
                                param.positionInQuery,
                                param.path,
                            )
                        } else {
                            addStatement(
                                "it.set${param.setterType}(%L, item.%L)",
                                param.positionInQuery,
                                param.path,
                            )
                        }
                    }
                    addStatement("it.addBatch()")
                }
                addStatement("it.executeBatch()")
            }
        }
    }
}


private fun TypeSpecBuilder.generateDeleteAllFunction(repo: Repo) {
    addFunction("deleteAll") {
        addModifiers(KModifier.OVERRIDE)
        addCode {
            addStatement("val query = %S", repo.deleteAllMethod.query)
            controlFlow("connection.prepareStatement(query).use") {
                addStatement("it.execute()")
            }
        }
    }
}

private fun TypeSpecBuilder.generateCheckFunction(repo: Repo) {
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
                    "%T(name = %S, nullable = %L, type = %T.%L )%L",
                    columnMember,
                    it.column.name,
                    it.column.nullable,
                    typeMember,
                    it.column.type,
                    separator
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

fun listParametrizedBy(qualifiedName: QualifiedName) =
    ClassName("kotlin.collections", "List")
        .parameterizedBy(qualifiedName.let { ClassName(it.pkg, it.name) })
