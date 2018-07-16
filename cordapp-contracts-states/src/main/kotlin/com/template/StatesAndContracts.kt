package com.template

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.util.*

// *****************
// * Contract Code *
// *****************
// This is used to identify our contract when building a transaction
val TEMPLATE_CONTRACT_ID = "com.template.TemplateContract"

class TemplateContract : Contract {
    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        // Verification logic goes here.
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Action : Commands
    }
}

// *********
// * State *
// *********
data class TemplateState(val issuer: Party,
                         val buyer: Party,
                         val propertyAddress: String,
                         val surveyDate: Date,
                         val expiryDate: Date,
                         val initialPrice: Int,
                         val resalePrice: Int,
                         override val linearId: UniqueIdentifier) : LinearState {
    override val participants: List<AbstractParty> get() = listOf()
}
