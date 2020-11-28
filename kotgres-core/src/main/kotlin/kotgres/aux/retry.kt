package kotgres.aux

import kotgres.aux.exception.OptimisticLockFailException
import kotlin.reflect.KClass

fun <R> retry(times: Int = 3, expect: KClass<out Throwable> = OptimisticLockFailException::class, block: () -> R): R {

    if (times < 1) throw IllegalArgumentException("'times' should be greater than 0")

    var counter = 0
    var lastCaughtEx: Exception

    do {
        try {
            return block()
        } catch (ex: Exception) {
            if (expect.isInstance(ex))
                lastCaughtEx = ex
            else
                throw ex
        }
        counter++
    } while (counter < times)

    throw lastCaughtEx
}
