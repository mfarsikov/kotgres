package kotgres.aux

import kotgres.all
import kotgres.aux.exception.OptimisticLockFailException
import kotgres.expect
import org.junit.jupiter.api.Test


internal class RetryTest {
    @Test
    fun `test retry`() {
        fun successAfter(times: Int): Boolean {
            var counter = 0
            return retry {
                counter++
                if (counter < times) throw OptimisticLockFailException()
                true
            }
        }

        all(
            { assert(successAfter(1)) },
            { assert(successAfter(2)) },
            { assert(successAfter(3)) },
            { expect<OptimisticLockFailException> { successAfter(4) } },
        )
    }
}