package com.survey.client

import com.survey.SurveyState
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.StateAndRef
import net.corda.core.utilities.NetworkHostAndPort.Companion.parse
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger

/**
 * Demonstration of how to use the CordaRPCClient to connect to a Corda Node and
 * stream the contents of the node's vault.
 */
fun main(args: Array<String>) = SurveyClient().main(args)

private class SurveyClient {
    companion object {
        val logger: Logger = loggerFor<SurveyClient>()
        private fun logState(state: StateAndRef<SurveyState>) = logger.info("{}", state.state.data)
    }

    fun main(args: Array<String>) {
        require(args.size == 1) { "Usage: SurveyClient <node address>" }
        val nodeAddress = parse(args[0])
        val client = CordaRPCClient(nodeAddress)

        // Can be amended in the com.survey.MainKt file.
        val proxy = client.start("user1", "test").proxy

        // Grab all existing SurveyStates and all future SurveyStates.
        val (snapshot, updates) = proxy.vaultTrack(SurveyState::class.java)

        // Log the existing SurveyStates and listen for new ones.
        snapshot.states.forEach { logState(it) }
        updates.toBlocking().subscribe { update ->
            update.produced.forEach { logState(it) }
        }
    }
}