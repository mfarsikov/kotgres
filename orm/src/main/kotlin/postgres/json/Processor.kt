package postgres.json

import postgres.json.lib.PostgresRepository
import postgres.json.lib.Table
import postgres.json.generator.DbDescription
import postgres.json.generator.generateDb
import postgres.json.generator.generateRepository
import postgres.json.mapper.toRepo
import postgres.json.model.repository.Repo
import postgres.json.parser.Parser
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.StandardLocation

class Processor : AbstractProcessor() {
    override fun getSupportedSourceVersion() = SourceVersion.latestSupported()
    override fun getSupportedOptions() = emptySet<String>()

    override fun getSupportedAnnotationTypes() = setOf(Table::class, PostgresRepository::class).mapTo(mutableSetOf()) { it.qualifiedName }

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)

        Logger.messager = processingEnv.messager
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {

        Logger.trace("Started kormp")

        if (roundEnv.processingOver()) return false

        val parser = Parser(roundEnv, processingEnv)

        val tableMarkedClasses = roundEnv.getElementsAnnotatedWith(Table::class.java)
        val repositories = roundEnv.getElementsAnnotatedWith(PostgresRepository::class.java)

        val repos = mutableListOf<Repo>()
        repositories.forEach{
            val parsedRepo = parser.parse(it)

            Logger.trace(parsedRepo)
            val repo = parsedRepo.toRepo(parser.parse(tableMarkedClasses.single()))
            repos += repo
            val repoFile = generateRepository(repo)


            val file = processingEnv.filer.createResource(
                StandardLocation.SOURCE_OUTPUT,
                repoFile.packageName,
                repoFile.name,
                it //TODO add all the elements
            )

            file.openWriter().use {
                repoFile.writeTo(it)
            }
        }

        val dbFile = generateDb(dbDescription = DbDescription(
            pkg = "my.pack",
            name = "DB",
            repositories = repos
        ))

        dbFile.name

        val file = processingEnv.filer.createResource(
            StandardLocation.SOURCE_OUTPUT,
            dbFile.packageName,
            dbFile.name,
            repositories.first() //TODO add all the elements
        )

        file.openWriter().use {
            dbFile.writeTo(it)
        }

//        tableMarkedClasses.forEach { element ->
//
//            val parsedKlass = parser.parse(element)
//            Logger.trace(parsedKlass)
//
//            val mapping = parsedKlass.toTableMapping()
//            Logger.trace(mapping)
//
//            val file = processingEnv.filer.createResource(
//                StandardLocation.SOURCE_OUTPUT,
//                "blabl_apackage",
//                "Testmpl.kt",
//                element
//            )
//
//            file.openWriter().use {
//                it.write(
//                    """
//                        fun hw(){println("HW")}
//                    """.trimIndent()
//                )
//            }
//        }

        return true
    }

}
