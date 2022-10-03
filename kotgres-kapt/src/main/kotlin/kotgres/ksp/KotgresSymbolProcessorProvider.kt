package kotgres.ksp

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import kotgres.ksp.model.klass.QualifiedName

class KotgresSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): KotgresSymbolProcessor {
        val s = environment.options["kotgres.db.qualifiedName"]
            ?: error("""Cannot find option 'kotgres.db.qualifiedName', please configure KSP in build.gradle: ksp { arg("kotgres.db.qualifiedName", "my.pack.DB")}""")
        val parts = s.split(".")
        val dbQualifiedName = QualifiedName(pkg = parts.dropLast(1).joinToString("."), name = parts.last())
        val options = KotgresOptions(
            dbQualifiedName,
        )
        return KotgresSymbolProcessor(environment.codeGenerator, environment.logger, options)
    }
}
