package kotgres.kapt.model.db

import kotgres.aux.ColumnDefinition
import kotgres.kapt.model.klass.Klass
import kotgres.kapt.model.klass.Type
import kotgres.kapt.model.repository.ObjectConstructor

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
