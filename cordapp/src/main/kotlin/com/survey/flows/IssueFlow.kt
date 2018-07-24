package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*

// *********
// * Issue flow *
// The mechanism for issuing a survey onto the ledger
// *********
@InitiatingFlow
@StartableByRPC
class Initiator : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Flow impl
    }
}

@InitiatedBy(Initiator::class)
class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Flow implementation goes here
    }
}