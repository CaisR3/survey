package com.survey

import net.corda.core.crypto.SecureHash
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import java.security.Key
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*
import javax.crypto.spec.SecretKeySpec


/**
 * A generic database service superclass for handling for database update.
 *
 * @param services The node's service hub.
 */
@CordaService
open class DatabaseService(private val services: ServiceHub) : SingletonSerializeAsToken() {

    val TABLE_NAME = "key_values"

    companion object {
        val log = loggerFor<DatabaseService>()
    }

    /**
     * Executes a database update.
     *
     * @param query The query string with blanks for the parameters.
     * @param params The parameters to fill the blanks in the query string.
     */
    protected fun executeUpdate(query: String, params: Map<Int, Any>) {
        val preparedStatement = prepareStatement(query, params)

        try {
            preparedStatement.executeUpdate()
        } catch (e: SQLException) {
            log.error(e.message)
            throw e
        } finally {
            preparedStatement.close()
        }
    }

    /**
     * Executes a database query.
     *
     * @param query The query string with blanks for the parameters.
     * @param params The parameters to fill the blanks in the query string.
     * @param transformer A function for processing the query's ResultSet.
     *
     * @return The list of transformed query results.
     */
    protected fun <T : Any> executeQuery(
            query: String,
            params: Map<Int, Any>,
            transformer: (ResultSet) -> T
    ): List<T> {
        val preparedStatement = prepareStatement(query, params)
        val results = mutableListOf<T>()

        return try {
            val resultSet = preparedStatement.executeQuery()
            while (resultSet.next()) {
                results.add(transformer(resultSet))
            }
            results
        } catch (e: SQLException) {
            log.error(e.message)
            throw e
        } finally {
            preparedStatement.close()
        }
    }

    /**
     * Creates a PreparedStatement - a precompiled SQL statement to be
     * executed against the database.
     *
     * @param query The query string with blanks for the parameters.
     * @param params The parameters to fill the blanks in the query string.
     *
     * @return The query string and params compiled into a PreparedStatement
     */
    private fun prepareStatement(query: String, params: Map<Int, Any>): PreparedStatement {
        val session = services.jdbcSession()
        val preparedStatement = session.prepareStatement(query)

        params.forEach { (key, value) ->
            when (value) {
                is String -> preparedStatement.setString(key, value)
                is Int -> preparedStatement.setInt(key, value)
                is Long -> preparedStatement.setLong(key, value)
                else -> throw IllegalArgumentException("Unsupported type.")
            }
        }

        return preparedStatement
    }

    fun addKey(payload: Pair<Int, Key>) {
        val query = "insert into $TABLE_NAME values(?, ?)"

        // get base64 encoded version of the key
        val encodedKey = Base64.getEncoder().encodeToString(payload.second.getEncoded())

        val params = mapOf(1 to payload.first, 2 to encodedKey)

        executeUpdate(query, params)
        log.info("Key for hash ${payload.first} added to ${TABLE_NAME} table.")
    }


    fun queryKeyValue(hash: SecureHash): Key {
        val query = "select value from $TABLE_NAME where token = ?"

        val params = mapOf(1 to hash)

        val results = executeQuery(query, params, { it -> it.getString("value") })

        if (results.isEmpty()) {
            throw IllegalArgumentException("Key $hash not present in database.")
        }

        val value = results.single()
        log.info("Token $hash read from ${TABLE_NAME} table.")

        val decodedKey = Base64.getDecoder().decode(value)
        return SecretKeySpec(decodedKey, 0, decodedKey.size, "AES")
    }

}