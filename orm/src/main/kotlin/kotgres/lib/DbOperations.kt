package kotgres.lib

import java.sql.Connection
import java.sql.Savepoint

interface DbOperations {
    fun rollback()
    fun rollbackTo(savepoint: Savepoint)
    fun savePoint(): Savepoint
}

class DbOperationsImpl(
    private val connection: Connection,
) : DbOperations {

    override fun rollback() {
        connection.rollback()
    }

    override fun rollbackTo(savepoint: Savepoint) {
        connection.rollback(savepoint)
    }

    override fun savePoint(): Savepoint {
        return connection.setSavepoint()
    }
}