package postgres.json

import postgres.json.generator.DbDescription
import postgres.json.generator.generateDb
import postgres.json.generator.generateRepository
import postgres.json.lib.PostgresRepository
import postgres.json.lib.Table
import postgres.json.mapper.toRepo
import postgres.json.model.klass.Klass
import postgres.json.model.repository.Repo
import postgres.json.parser.Parser
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.StandardLocation

class Processor : AbstractProcessor() {
    override fun getSupportedSourceVersion() = SourceVersion.latestSupported()
    override fun getSupportedOptions() = emptySet<String>()

    override fun getSupportedAnnotationTypes() =
        setOf(Table::class, PostgresRepository::class).mapTo(mutableSetOf()) { it.qualifiedName }

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)

        Logger.messager = processingEnv.messager
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {

        if (roundEnv.processingOver()) return false

        val parser = Parser(roundEnv, processingEnv)

        val repositories = roundEnv.getElementsAnnotatedWith(PostgresRepository::class.java)

        val repos = mutableListOf<Repo>()
        repositories.forEach { repoElement ->
            val parsedRepo = parser.parse(repoElement)

            Logger.trace(parsedRepo)
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
                pkg = "my.pack",
                name = "DB",
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
