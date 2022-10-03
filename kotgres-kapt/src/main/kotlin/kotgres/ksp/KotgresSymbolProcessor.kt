package kotgres.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import kotgres.ksp.Postgres.mapper.toRepo
import kotgres.ksp.Postgres.mapper.validationErrors
import kotgres.ksp.generator.DbDescription
import kotgres.ksp.generator.generateDb
import kotgres.ksp.generator.generateRepository
import kotgres.ksp.model.klass.Klass

class KotgresSymbolProcessor(
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger,
    val options: KotgresOptions,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val visitor = KotgresSymbolVisitor(logger)
        val classes = resolver.getSymbolsWithAnnotation("kotgres.annotations.PostgresRepository")
            .toList()
            .map { it.accept(visitor, Unit) as Klass }

        classes.forEach { cls ->
            validationErrors(cls).takeIf { it.isNotEmpty() }?.forEach { err -> logger.error(err) }
        }
        val repos = classes.map { it.toRepo(options.dbQualifiedName) }

        logger.info("repos: $repos")

        val repoFiles = repos.map { generateRepository(it) }

        // TODO set dependencies
        repoFiles.forEach { fileSpec ->
            val f = codeGenerator.createNewFile(Dependencies.ALL_FILES, fileSpec.packageName, fileSpec.name)

            val b = StringBuilder().also {
                fileSpec.writeTo(it)
            }
            f.write(b.toString().toByteArray())
        }

        repos.groupBy { it.belongsToDb }.forEach { (dbQualifiedName, repos) ->
            val dbFile = generateDb(
                dbDescription = DbDescription(
                    pkg = dbQualifiedName.pkg,
                    name = dbQualifiedName.name,
                    repositories = repos,
                    spring = false, // TODO
                ),
            )

            val file = codeGenerator.createNewFile(
                Dependencies.ALL_FILES, // TODO
                dbFile.packageName,
                dbFile.name,
            )

            val b = StringBuilder().also {
                dbFile.writeTo(it)
            }
            file.write(b.toString().toByteArray())
        }

        return emptyList()
    }
}
