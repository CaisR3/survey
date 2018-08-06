package com.template.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow

/**
 * Self issues the calling node an amount of cash in the desired currency.
 * Only used for demo/sample/iou purposes!
 */
@StartableByRPC
@InitiatingFlow
class SelfIssueCashFlow() : FlowLogic<Cash.State>() {

    @Suspendable
    override fun call(): Cash.State {
        val issueRef = OpaqueBytes.of(0)
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        val cashIssueSubflowResult = subFlow(CashIssueFlow(1000000.POUNDS, issueRef, notary))

        val cashIssueTx = cashIssueSubflowResult.stx.toLedgerTransaction(serviceHub)
        return cashIssueTx.outputsOfType<Cash.State>().single()
    }
}