package my.pack

import kotlin.test.fail

inline fun <reified E : Throwable> expect(block: () -> Any?) {
    try {
        val r = block()
        fail("Expected ${E::class.qualifiedName}, but nothing was thrown, and returned: $r")
    } catch (fail: AssertionError) {
        throw fail
    } catch (actual: Throwable) {
        assert(actual::class == E::class)
    }
}

fun all(vararg r: () -> Unit) {

    val exs = r.mapNotNull {
        try {
            it()
            null
        } catch (ex: AssertionError) {
            ex.message
        }
    }
    if (exs.isNotEmpty())
        throw AssertionError(exs.joinToString(separator = "\n\n"))
}