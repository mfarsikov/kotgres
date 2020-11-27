package kotgres.aux.sort

import kotgres.all
import kotgres.expect
import org.junit.jupiter.api.Test

class OrderTest {
    @Test
    fun happy() {

        all(
            { assert(Order(emptyList()).stringify() == "") },
            { assert(Order(listOf(SortCol("x"))).stringify() == "ORDER BY x") },
            {
                val order1 = Order(
                    listOf(
                        SortCol(
                            "x",
                            SortOrder.DESC,
                            NullsOrder.NULLS_FIRST
                        )
                    )
                )
                assert(order1.stringify() == "ORDER BY x DESC NULLS FIRST")
            },
            {
                val order2 = Order(
                    listOf(
                        SortCol("x", SortOrder.DESC, NullsOrder.NULLS_FIRST),
                        SortCol("y", SortOrder.DESC, NullsOrder.NULLS_FIRST),
                    )
                )
                assert(order2.stringify() == "ORDER BY x DESC NULLS FIRST, y DESC NULLS FIRST")
            },
        )
    }

    @Test
    fun `field name validation`() {
        expect<Exception> {
            Order(listOf(SortCol(";"))).stringify()
        }
    }
}