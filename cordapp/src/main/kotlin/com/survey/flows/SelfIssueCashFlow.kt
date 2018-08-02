package com.template.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import java.util.*

/**
 * Self issues the calling node an amount of cash in the desired currency.
 * Only used for demo/sample/iou purposes!
 */
@StartableByRPC
@InitiatingFlow
class SelfIssueCashFlow(val amount: Amount<Currency>) : FlowLogic<Cash.State>() {

    override val progressTracker: ProgressTracker = SelfIssueCashFlow.tracker()

    companion object {
        object PREPARING : ProgressTracker.Step("Preparing to self issue cash.")
        object ISSUING : ProgressTracker.Step("Issuing cash")

        fun tracker() = ProgressTracker(PREPARING, ISSUING)
    }

    @Suspendable
    override fun call(): Cash.State {
        progressTracker.currentStep = PREPARING
        val issueRef = OpaqueBytes.of(0)
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        progressTracker.currentStep = ISSUING
        val cashIssueSubflowResult = subFlow(CashIssueFlow(amount, issueRef, notary))

        val cashIssueTx = cashIssueSubflowResult.stx.toLedgerTransaction(serviceHub)
        return cashIssueTx.outputsOfType<Cash.State>().single()
    }
}