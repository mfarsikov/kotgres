package kotgres.ksp.model.db

import kotgres.aux.ColumnDefinition
import kotgres.ksp.model.klass.Klass
import kotgres.ksp.model.klass.Type
import kotgres.ksp.model.repository.ObjectConstructor

data class TableMapping(
    val name: String,
    val schema: String?,
    val klass: Klass,
    val columns: List<ColumnMapping>,
    val objectConstructor: ObjectConstructor?,
)

data class ColumnMapping(
    val path: List<String>,
    val type: Type,
    val column: ColumnDefinition,
)
