package kotgres.aux.sort

import org.junit.jupiter.api.Test

class SortTest {
    @Test
    fun `check allowed column names`() {
        assert(SortCol("valid").isValid()) { "single word" }
        assert(SortCol("valid_column").isValid()) { "underscores" }
        assert(SortCol("valid_column_2").isValid()) { "numbers" }
        assert(SortCol("\"valid column\"").isValid()) { "quoted spaces" }
        assert(SortCol("\"Valid Column\"").isValid()) { "quoted upper case" }
        assert(SortCol("ValidColumn").isValid()) { "upper case" }

        assert(SortCol("--").isValid().not()) { "comments" }
        assert(SortCol(";").isValid().not()) { "semicolons" }
        assert(SortCol("invalid column").isValid().not()) { "unquoted spaces" }
    }
}