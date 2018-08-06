package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import com.survey.DatabaseService
import com.survey.SurveyState
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.node.services.AttachmentId
import net.corda.core.utilities.unwrap
import java.security.Key

/** Called by the oracle to provide a stock's spot price to a client. */
@InitiatedBy(IssueFlow.IssueSurveyFlow::class)
class GiveOracleKey(private val counterpartySession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call(): Unit {
        val payload = counterpartySession.receive<Pair<AttachmentId, ByteArray>>().unwrap { it }

        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        // BE CAREFUL when accessing the node's database in flows:
        // 1. The operation must be executed in a BLOCKING way. Flows don't
        //    currently support suspending to await a database operation's
        //    response
        // 2. The operation must be idempotent. If the flow fails and has to
        //    restart from a checkpoint, the operation will also be replayed
        try {
            databaseService.addKey(payload)
        } catch(e: Exception) {
            counterpartySession.send(false)
        }

        counterpartySession.send(true)
    }
}

@InitiatedBy(RequestKeyFlow.Initiator::class)
class QueryOracleForKey(private val counterpartySession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call(): Unit {
        val surveyState = counterpartySession.receive<SurveyState>().unwrap { it }
        val counterParty = counterpartySession.counterparty

        // TODO we should check that the survey state was issued by surveyor

        // check survey is now owned by the party requesting key
        if(surveyState.owner != counterParty) throw FlowException("Invalid owner requesting key")

        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val key = databaseService.queryKeyValue(surveyState.surveyHash)

        counterpartySession.send(key)
    }
}