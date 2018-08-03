package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import com.survey.SurveyKeyState
import com.survey.SurveyState
import com.survey.service.Oracle
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.core.utilities.unwrap

/** Called by the oracle to provide a stock's spot price to a client. */
class GiveOracleKey(private val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val flow = object : SignTransactionFlow(counterpartySession) {
            @Suspendable
            override fun checkTransaction(stx: SignedTransaction) {
                //Here we would check that the key state provided is the same as issued by a surveyor
            }
        }

        val stx = subFlow(flow)
        return waitForLedgerCommit(stx.id)
    }
}

//@InitiatedBy(QueryOracle::class)
class RequestOracleSignature(private val counterpartySession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call(): Unit {
        val ftx = counterpartySession.receive<FilteredTransaction>().unwrap { it }

        val signature = try {
            serviceHub.cordaService(Oracle::class.java).sign(ftx)
        } catch (e: Exception) {
            throw FlowException(e)
        }

        counterpartySession.send(signature)
    }
}

//@InitiatedBy(QueryOracle::class)
class QueryOracleForKey(private val counterpartySession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call(): Unit {
        val surveyState = counterpartySession.receive<SurveyState>().unwrap { it }
        val counterParty = counterpartySession.counterparty

        // TODO we should check that the survey state was issued by surveyor

        // check survey is now owned by the party requesting key
        if(surveyState.owner != counterParty) throw FlowException("Invalid owner requesting key")

        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(null, listOf(surveyState.linearId))
        val surveyKey = serviceHub.vaultService.queryBy<SurveyKeyState>(queryCriteria).states.firstOrNull() ?: throw FlowException("Key not found")

        counterpartySession.send(surveyKey)
    }
}