package my.pack

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class BlobTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun createTable() {
            TestUtil.runMigrations()
        }
    }

    val db = BlobDb(TestUtil.ds)

    @AfterEach
    fun cleanup() {
        db.transaction { blobRepository.deleteAll() }
    }

    @Test
    fun check() {
        assert(db.check().isEmpty())
    }

    @Test
    fun save() {
        val blob = Blob("some text".toByteArray())
        db.transaction { blobRepository.save(blob) }

        val blobs = db.transaction { blobRepository.findAll() }

        assert(String(blobs.single().byteArray) == "some text")
    }
}