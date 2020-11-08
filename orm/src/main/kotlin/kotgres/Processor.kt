package kotgres

import kotgres.generator.DbDescription
import kotgres.generator.generateDb
import kotgres.generator.generateRepository
import kotgres.lib.PostgresRepository
import kotgres.lib.Table
import kotgres.mapper.toRepo
import kotgres.mapper.validationErrors
import kotgres.model.klass.Klass
import kotgres.model.repository.Repo
import kotgres.parser.Parser
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.StandardLocation

class Processor : AbstractProcessor() {
    var dbQualifiedName: String? = null
    override fun getSupportedSourceVersion() = SourceVersion.latestSupported()
    override fun getSupportedOptions() = setOf(
        "kotgres.log.level",
        "kotgres.db.qualifiedName"
    )

    override fun getSupportedAnnotationTypes() =
        setOf(Table::class, PostgresRepository::class).mapTo(mutableSetOf()) { it.qualifiedName }

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)

        Logger.messager = processingEnv.messager
        processingEnv.options["kotgres.log.level"]
            ?.also { Logger.logLevel = Logger.LogLevel.valueOf(it.toUpperCase()) }


        dbQualifiedName = processingEnv.options["kotgres.db.qualifiedName"]
        if (dbQualifiedName == null) Logger.error("kotgres.db.qualifiedName is not specified")
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

            val repo = parsedRepo.toRepo()
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

        val dbFile = generateDb(
            dbDescription = DbDescription(
                pkg = dbQualifiedName!!.substringBeforeLast("."),
                name = dbQualifiedName!!.substringAfterLast("."),
                repositories = repos
            )
        )

        val file = processingEnv.filer.createResource(
            StandardLocation.SOURCE_OUTPUT,
            dbFile.packageName,
            dbFile.name,
            *repositories.toTypedArray()
        )

        file.openWriter().use {
            dbFile.writeTo(it)
        }

        return true
    }

}

private fun elementsFromRepo(klass: Klass): List<Element> {
    return elementsFromFromKlass(klass.superclassParameter!!.klass) + klass.element!!
}

private fun elementsFromFromKlass(klass: Klass): List<Element> {
    if (klass.element == null) return emptyList()

    return klass.fields.flatMap { elementsFromFromKlass(it.type.klass) } + klass.element
}
