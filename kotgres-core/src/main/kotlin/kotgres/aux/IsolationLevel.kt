package kotgres.aux

import java.sql.Connection

enum class IsolationLevel(
    val javaSqlValue:Int
) {
    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
    REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE)
}