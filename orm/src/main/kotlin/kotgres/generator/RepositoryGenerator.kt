package kotgres.generator

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
import kotgres.lib.Checker
import kotgres.model.klass.Klass
import kotgres.model.klass.Nullability
import kotgres.model.klass.QualifiedName
import kotgres.model.klass.Type
import kotgres.model.repository.ObjectConstructor
import kotgres.model.repository.QueryMethod
import kotgres.model.repository.Repo
import kotgres.parser.KotlinType
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

            generateSaveAllFunction(repo)
            generateFindAllFunction(repo)
            generateCheckFunction(repo)
            generateSaveFunction(repo)
            generateDeleteAllFunction(repo)
            repo.queryMethods.forEach { queryMethod ->
                generateCustomSelectFunction(queryMethod, repo)
            }
        }
    }
}

private fun TypeSpecBuilder.generateCustomSelectFunction(
    queryMethod: QueryMethod,
    repo: Repo
) {
    addFunction(queryMethod.name) {
        addModifiers(KModifier.OVERRIDE)
        returns(queryMethod.returnType.toTypeName())
        addParameters(queryMethod.queryParameters.map { param ->
            ParameterSpec(
                name = param.path,
                type = param.type.toTypeName()
            )
        })

        addCode {
            addStatement("val query = %S", queryMethod.query)
            controlFlow("return connection.prepareStatement(query).use") {
                queryMethod.queryParameters.forEachIndexed { i, param ->
                    if(param.isEnum) {
                        addStatement("it.setString(%L, %L.name)", i + 1, param.path)
                    }else{
                        addStatement("it.set%L(%L, %L)", param.setterType, i + 1, param.path)
                    }
                }
                if (queryMethod.returnType.klass.name != KotlinType.UNIT.qn) {
                    controlFlow("it.executeQuery().use") {
                        if (queryMethod.returnsCollection)
                            generateCollectionExtractor(repo)
                        else
                            generateSingleElementExtractor(queryMethod, repo, queryMethod.returnType)
                    }
                } else {
                    addStatement("it.execute()")
                }
            }
        }
    }
}

private fun CodeBlockBuilder.generateCollectionExtractor(repo: Repo) {
    addStatement("val acc = mutableListOf<%T>()", repo.mappedKlass.klass.toClassName())
    `while`("it.next()") {
        addStatement("acc +=")
        indent()
        generateConstructorCall(repo.mappedKlass.objectConstructor)
        unindent()
    }
    addStatement("acc")
}

private fun CodeBlockBuilder.generateSingleElementExtractor(
    queryMethod: QueryMethod,
    repo: Repo,
    returnType: Type
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
        generateConstructorCall(repo.mappedKlass.objectConstructor)
    } `else` {
        if (returnType.nullability == Nullability.NULLABLE)
            addStatement("null")
        else
            addStatement("throw %T()", NoSuchElementException::class)
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

private fun TypeSpecBuilder.generateSaveFunction(repo: Repo) {
    addFunction("save") {
        addModifiers(KModifier.OVERRIDE)
        addParameter(ParameterSpec("item", repo.mappedKlass.klass.name.let { ClassName(it.pkg, it.name) }))
        addStatement("saveAll(listOf(item))")
    }
}

private fun TypeSpecBuilder.generateFindAllFunction(repo: Repo) {
    addFunction("findAll") {
        addModifiers(KModifier.OVERRIDE)
        returns(listParametrizedBy(repo.mappedKlass.klass.name))

        val entityType = repo.findAllMethod.returnType.typeParameters.single()

        addCode {
            addStatement("val query = %S", repo.findAllMethod.query)

            val entityClassName = ClassName(entityType.klass.name.pkg, entityType.klass.name.name)
            controlFlow("return connection.prepareStatement(query).use") {
                controlFlow("it.executeQuery().use") {
                    addStatement("val acc = mutableListOf<%T>()", entityClassName)
                    `while`("it.next()") {
                        addStatement("acc += ")

                        generateConstructorCall(repo.mappedKlass.objectConstructor)

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
            } else if(c.isEnum){
                addStatement("%L = %M.valueOf(it.getString(%S)),",
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

private fun TypeSpecBuilder.generateSaveAllFunction(repo: Repo) {
    addFunction("saveAll") {
        addModifiers(KModifier.OVERRIDE)
        addParameter(
            ParameterSpec(
                "items", listParametrizedBy(repo.mappedKlass.klass.name)
            )
        )
        addCode {
            addStatement("val query = %S", repo.saveAllMethod.query)
            controlFlow("connection.prepareStatement(query).use") {
                `for`("item in items") {
                    for (param in repo.saveAllMethod.queryParameters) {
                        if (param.isJson) {
                            addStatement(
                                """it.setObject(%L, %M().apply { type = "jsonb"; value = %M.%M(item.%L) })""",
                                param.position,
                                MemberName("org.postgresql.util", "PGobject"),
                                MemberName("kotlinx.serialization.json", "Json"),
                                MemberName("kotlinx.serialization", "encodeToString"),
                                param.path,
                            )
                        } else if (param.isEnum) {
                            addStatement(
                                "it.setString(%L, item.%L.name)",
                                param.position,
                                param.path,
                            )
                        } else {
                            addStatement(
                                "it.set${param.setterType}(%L, item.%L)",
                                param.position,
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
            val columnMember = ClassName("kotgres.model.db", "ColumnDefinition")
            val typeMember = ClassName("kotgres.model.db", "PostgresType")

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
