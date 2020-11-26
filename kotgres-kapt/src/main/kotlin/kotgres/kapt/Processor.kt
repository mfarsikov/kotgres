package kotgres.kapt

import kotgres.annotations.PostgresRepository
import kotgres.kapt.generator.DbDescription
import kotgres.kapt.generator.generateDb
import kotgres.kapt.generator.generateRepository
import kotgres.kapt.mapper.toRepo
import kotgres.kapt.mapper.validationErrors
import kotgres.kapt.model.klass.Klass
import kotgres.kapt.model.repository.Repo
import kotgres.kapt.parser.Parser
import kotgres.kapt.parser.toQualifiedName
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.StandardLocation

class Processor : AbstractProcessor() {
    var dbQualifiedName: String? = null
    var spring: String? = null
    override fun getSupportedSourceVersion() = SourceVersion.latestSupported()
    override fun getSupportedOptions() = setOf(
        "kotgres.log.level",
        "kotgres.db.qualifiedName",
        "kotgres.spring"
    )

    override fun getSupportedAnnotationTypes() =
        setOf(PostgresRepository::class).mapTo(mutableSetOf()) { it.qualifiedName }

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)

        Logger.messager = processingEnv.messager
        processingEnv.options["kotgres.log.level"]
            ?.also { Logger.logLevel = Logger.LogLevel.valueOf(it.toUpperCase()) }

        spring = processingEnv.options["kotgres.spring"]

        dbQualifiedName = processingEnv.options["kotgres.db.qualifiedName"]
        if (dbQualifiedName == null) Logger.error("kotgres.db.qualifiedName is not specified")//TODO advice
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {

        if (roundEnv.processingOver()) return false

        val parser = Parser(roundEnv, processingEnv)

        val repositories = roundEnv.getElementsAnnotatedWith(PostgresRepository::class.java)

        val repos = mutableListOf<Repo>()
        repositories.forEach { repoElement ->
            val parsedRepo = parser.parse(repoElement)

            Logger.trace("Parsed repository: $parsedRepo")

            val errors = validationErrors(parsedRepo)

            if (errors.isNotEmpty()) {
                errors.forEach { Logger.error(it) }
                return@forEach
            }

            val repo = parsedRepo.toRepo(dbQualifiedName!!.toQualifiedName())
            repos += repo
            val repoFile = generateRepository(repo)

            val file = processingEnv.filer.createResource(
                StandardLocation.SOURCE_OUTPUT,
                repoFile.packageName,
                repoFile.name,
                *elementsFromRepo(parsedRepo).toTypedArray()
            )

            file.openWriter().use {
                repoFile.writeTo(it)
            }
        }

        repos.groupBy { it.belongsToDb }.forEach { (dbQualifiedName, repos) ->
            val dbFile = generateDb(
                dbDescription = DbDescription(
                    pkg = dbQualifiedName.pkg,
                    name = dbQualifiedName.name,
                    repositories = repos,
                    spring = spring?.toBoolean() ?: false,
                )
            )

            val file = processingEnv.filer.createResource(
                StandardLocation.SOURCE_OUTPUT,
                dbFile.packageName,
                dbFile.name,
                *repos.map { it.superKlass.element }.toTypedArray()
            )

            file.openWriter().use {
                dbFile.writeTo(it)
            }
        }

        return true
    }

}

private fun elementsFromRepo(klass: Klass): List<Element> {
    return (klass.superclassParameter?.let{elementsFromFromKlass(it.klass)}?: emptyList()) + klass.element!!
}

private fun elementsFromFromKlass(klass: Klass): List<Element> {
    if (klass.element == null) return emptyList()

    return klass.fields.flatMap { elementsFromFromKlass(it.type.klass) } + klass.element
}
