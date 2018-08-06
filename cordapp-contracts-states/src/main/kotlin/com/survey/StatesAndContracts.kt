package com.survey

import com.google.common.hash.HashCode
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.utils.sumCash


// *****************
// * Contract Code *
// *****************
// This is used to identify our contract when building a transaction

class SurveyContract : Contract {

    companion object {
        @JvmStatic
        val SURVEY_CONTRACT_ID = "com.survey.SurveyContract"
    }
    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when(command.value){
            is Commands.IssueRequest -> {
                requireThat {
                    // Input and output states.
                    "There should be at least one input cash state." using (tx.inputStates.size >= 1)
                    "There should be at least two output states, cash and survey request." using (tx.outputStates.size >= 2)

                    val inputCash = tx.inputsOfType<Cash.State>()
                    val outputCash = tx.outputsOfType<Cash.State>()
                    val outputSurveyRequest = tx.outputsOfType<SurveyRequestState>().single()
                    "The survey price must positive." using (outputSurveyRequest.surveyPrice > 0)
                    "The cash must be greater than the survey price." using (inputCash.sumCash().quantity.toInt() >= outputSurveyRequest.surveyPrice)
                    "The input cash must be equal to the output cash." using (inputCash.sumCash().quantity == outputCash.sumCash().quantity)
                    "The output survey status is pending." using (outputSurveyRequest.status == "pending")

                    // Owners and signers.
                    "The input cash owner is the requester." using (inputCash.first().owner == outputSurveyRequest.requester)
                    "The output cash owner is the surveyor." using (outputCash.first().owner == outputSurveyRequest.surveyor)
                    "The surveyor and requester must be signers." using (command.signers.containsAll(outputSurveyRequest.participants.map { it.owningKey }))
                    "The input cash owner must be signer." using (command.signers.containsAll(inputCash.first().participants.map { it.owningKey }))
                }
            }
            is Commands.Issue -> {
                val outputSurvey = tx.outputsOfType<SurveyState>().single()
                requireThat {
                    // Input and output states.
                    "One input state should be consumed, the survey request." using (tx.inputStates.size == 1)
                    "Two output states should be created, the survey and the updated survey request." using (tx.outputs.size == 2)

                    val inputSurveyRequest = tx.inputsOfType<SurveyRequestState>().single()
                    val outputSurveyKey = tx.outputsOfType<SurveyKeyState>().singleOrNull()
                    val outputSurveyRequest = tx.outputsOfType<SurveyRequestState>().single()
                    // Sample constraints.
                    "The survey's initial price must be positive." using (outputSurvey.initialPrice > 0)
                    "The initial price must be equal to the resale price." using (outputSurvey.initialPrice == outputSurvey.resalePrice)
                    "The output survey request status is complete." using (outputSurveyRequest.status == "complete")

                    // Owners and signers.
                    "The purchase requester is the final survey owner." using (inputSurveyRequest.requester == outputSurvey.owner)
                    "Issuer must be the signer." using (command.signers.contains(outputSurvey.issuer.owningKey))
                    "Purchaser must be the signer." using (command.signers.contains(outputSurvey.owner.owningKey))
                }
                try {
                    tx.getAttachment(outputSurvey.surveyHash)
                } catch(e: Exception) {
                    throw kotlin.IllegalArgumentException("Attachment missing or does not match hash.")
                }
            }
            is Commands.Trade -> {
                requireThat {
                    // Input and output states.
                    val inputCash = tx.inputsOfType<Cash.State>().toList()
                    val inputSurvey = tx.inputsOfType<SurveyState>().single()
                    val outputSurvey = tx.outputsOfType<SurveyState>().single()
                    val outputCash = tx.outputsOfType<Cash.State>().single()
                    "There should be one input survey state" using (tx.inputsOfType<SurveyState>().size == 1)
                    "There should be one output survey state" using (tx.outputsOfType<SurveyState>().size == 1)
                    "There should be at least one input cash state" using (tx.inputsOfType<Cash.State>().size > 1)
                    "There should be at least one output cash state" using (tx.outputsOfType<Cash.State>().size > 1)
                    "The initial and final hashes must be the same." using (inputSurvey.surveyHash == outputSurvey.surveyHash)
                    "Input cash should be at least equal to the resale price" using (inputCash.sumCash().quantity.toInt() >= outputSurvey.resalePrice)
                    "Output cash to the owner should be equal to resale price" using (outputCash.ownedBy(outputSurvey.owner).amount.quantity.toDouble() == (inputSurvey.resalePrice * 0.8))
                    "Output cash to issuer is equal to 20% of the resale price" using (outputCash.ownedBy(outputSurvey.issuer).amount.quantity.toDouble() == (inputSurvey.resalePrice * 0.2))

                    // Owners and signers.
                    "The owner of the input cash, now owns the survey." using (inputCash.first().owner == outputSurvey.owner)
                    "The owner of the survey initially, now owns the cash." using (inputSurvey.owner == outputCash.owner)
                    "Cannot sell survey to yourself." using (inputSurvey.owner != outputSurvey.owner)
                    "All of the survey participants must be signers." using (command.signers.containsAll(inputSurvey.participants.map { it.owningKey }))
                    "The cash owner must be signer." using (command.signers.contains(inputCash.single().owner.owningKey))
                }
            }
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class IssueRequest : Commands
        class Issue : Commands
        class Trade : Commands
    }
}

// *********
// * State *
// *********
data class SurveyState(val issuer: Party,
                       val owner: Party,
                       val landTitleId: String,
                       val initialPrice: Int,
                       val resalePrice: Int,
                       val surveyHash: SecureHash,
                       override val linearId: UniqueIdentifier) : LinearState {
    override val participants: List<AbstractParty> get() = listOf(issuer, owner)
}

// *********
// * SurveyKeyState
// * The facts shared by the surveyor to
// * the buyer once the survey is complete
// *********

data class SurveyKeyState(val encodedSurveyKey: String,
                          override val linearId: UniqueIdentifier) : LinearState {
    override val participants: List<AbstractParty> get() = listOf()
}

// *********
// * SurveyRequestState
// * For potential to make a request for a survey
// * at a particular address for price.
// *********
data class SurveyRequestState(val requester: Party,
                              val surveyor: Party,
                              val landTitleId: String,
                              val surveyPrice: Int,
                              val status: String,
                              override val linearId: UniqueIdentifier = UniqueIdentifier(landTitleId)) : LinearState {
    override val participants: List<AbstractParty> get() = listOf(requester, surveyor)
}