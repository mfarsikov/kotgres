package my.pack

import kotgres.aux.exception.OptimisticLockFailException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.*

internal class OptimisticallyLockedItemTest {
    companion object {

        @JvmStatic
        @BeforeAll
        fun createTable() {
            TestUtil.runMigrations()
        }


    }

    val db = DB(TestUtil.ds)

    @AfterEach
    fun cleanup() {
        db.transaction { optimisticLockRepository.deleteAll() }
    }

    @Test
    fun t() {
        val id = UUID.fromString("b49aa3b4-359b-4dd2-9e7d-6141d28cc748")
        val item = OptimisticallyLockedItem(id, version = 0)

        db.transaction { optimisticLockRepository.save(item) }

        val itemV1 = db.transaction { optimisticLockRepository.find(id)!! }

        assert(itemV1.version == 1) { "item created and version updated from 0 to 1" }

        expect<OptimisticLockFailException> { // cannot update entity with same ID and null version
            db.transaction { optimisticLockRepository.save(item) }
        }

        db.transaction { optimisticLockRepository.save(itemV1) }

        expect<OptimisticLockFailException> { // cannot update entity using stale state
            db.transaction { optimisticLockRepository.save(itemV1) }
        }

        val itemV2 = db.transaction { optimisticLockRepository.find(id)!! }

        assert(itemV2.version == 2) { "version is updated to 2" }

        expect<OptimisticLockFailException> {
            db.transaction { optimisticLockRepository.delete(itemV1) }
        }

        db.transaction { optimisticLockRepository.delete(itemV2) }

        val res = db.transaction { optimisticLockRepository.find(id) }

        assert(res == null)
    }
}