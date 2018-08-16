package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import com.survey.SurveyState
import net.corda.core.flows.*
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash

object OracleIssueKeyFlow{

    // *************
    // OracleIssueKeyFlow : responder flow that sends a decoding key to a survey buyer
    // once they are verified as payed owner of the survey
    // *************
    @InitiatedBy(RequestKeyFlow.Initiator::class)
    @StartableByRPC
    class Responder(val counterPartySession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call(): Unit {
            // receive tx builder or simple request for key? But how do we prove to oracle that buyer did pay for the survey?
            // link with counterPartySession
            // retrieve key for corresponding hash
            // verify requester has payed for the survey - via transaction (with seller) or through being owner of valid state
            // if valid, add decoding key or send via session to buyer
            // feel like need to involve a transaction here to prove buyer integrity, or atleast send the tx.
        }
    }
}