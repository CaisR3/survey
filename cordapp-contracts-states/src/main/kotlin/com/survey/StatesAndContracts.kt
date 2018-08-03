package com.survey

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash



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
                    "There should be one input state, cash." using (tx.inputStates.size == 1)
                    "There should be two output states, cash and survey request." using (tx.outputStates.size == 2)

                    val inputCash = tx.inputsOfType<Cash.State>().single()
                    val outputCash = tx.outputsOfType<Cash.State>().single()
                    val outputSurveyRequest = tx.outputsOfType<SurveyRequestState>().single()
                    "The survey price must positive." using (outputSurveyRequest.surveyPrice > 0)
                    "The survey price must be equal to the cash." using (inputCash.amount.quantity.toInt() == outputSurveyRequest.surveyPrice)
                    "The input cash must be equal to the output cash." using (inputCash.amount.quantity == outputCash.amount.quantity)
                    "The output survey status is pending." using (outputSurveyRequest.status == "Pending")

                    // Owners and signers.
                    "The input cash owner is the requester." using (inputCash.owner == outputSurveyRequest.requester)
                    "The output cash owner is the surveyor." using (outputCash.owner == outputSurveyRequest.surveyor)
                    "The surveyor and requester must be signers." using (command.signers.containsAll(outputSurveyRequest.participants.map { it.owningKey }))
                    "The input cash owner must be signer." using (command.signers.containsAll(inputCash.participants.map { it.owningKey }))
                }
            }
            is Commands.Issue -> {
                val outputSurvey = tx.outputsOfType<SurveyState>().single()
                requireThat {
                    // Input and output states.
                    "One input state should be consumed, the survey request." using (tx.inputStates.size == 1)
                    "Three output states should be created, the survey, the survey key, and the survey request." using (tx.outputs.size == 3)

                    val inputSurveyRequest = tx.inputsOfType<SurveyRequestState>().single()
                    val outputSurveyKey = tx.outputsOfType<SurveyKeyState>().singleOrNull()
                    val outputSurveyRequest = tx.outputsOfType<SurveyRequestState>().single()
                    // Sample constraints.
                    "The survey's initial price must be positive." using (outputSurvey.initialPrice > 0)
                    "The initial price must be equal to the resale price." using (outputSurvey.initialPrice == outputSurvey.resalePrice)
                    "The output survey request status is complete." using (outputSurveyRequest.status == "Complete")
                    "The key is included." using (outputSurveyKey != null)

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
            is Commands.SendKey -> {
                requireThat {
                    // Input and output states.
                    "There should be one input state, the key." using (tx.inputStates.size == 1)
                    "There should be one output state, the key." using (tx.outputStates.size == 1)
                    val inputSurveyKey = tx.inputsOfType<SurveyKeyState>().single()
                    val outputSurveyKey = tx.outputsOfType<SurveyKeyState>().single()
                    "The key should be the same before and after." using (inputSurveyKey.encodedSurveyKey == outputSurveyKey.encodedSurveyKey)
                }
            }
            is Commands.Trade -> {
                requireThat {
                    // Input and output states.
                    val inputCash = tx.inputsOfType<Cash.State>().single()
                    val inputSurvey = tx.inputsOfType<SurveyState>().single()
                    val inputSurveyKey = tx.inputsOfType<SurveyKeyState>().single()
                    val outputSurvey = tx.outputsOfType<SurveyState>().single()
                    val outputSurveyKey = tx.outputsOfType<SurveyKeyState>().single()
                    val outputCash = tx.outputsOfType<Cash.State>().single()
                    "There should be one input survey state" using (tx.inputsOfType<SurveyState>().size == 1)
                    "There should be one output survey state" using (tx.outputsOfType<SurveyState>().size == 1)

                    "There should be at least input cast state" using (tx.inputsOfType<Cash>().size == 1)
                    "There should be one output cast state" using (tx.outputsOfType<Cash>().size == 1)                            "" +



                    **************************




                    "The initial and final hashes must be the same." using (inputSurveyKey.encodedSur veyHash == outputSurveyKey.encodedSurveyHash)
                    "The initial and final keys must the same." using (inputSurveyKey.encodedSurveyKey == outputSurveyKey.encodedSurveyKey)
                    "Input cash should be equal to the resale price" using (inputCash.amount.quantity.toInt() == outputSurvey.resalePrice)
                    "Output cash should be equal to resale price" using (outputCash.amount.quantity.toInt() == inputSurvey.resalePrice)

                    // Owners and signers.
                    "The person who owns the cash initially, now owns the survey." using (inputCash.owner == outputSurvey.owner)
                    "The person who owns the cash initially, now owns the survey key." using (inputCash.owner == outputSurveyKey.owner)
                    "The person who owns survey initially, now owns the cash." using (inputSurvey.owner == outputCash.owner)
                    "The person who owns survey key initially, now owns the cash." using (inputSurveyKey.owner == outputCash.owner)
                    "Cannot sell survey to yourself." using (inputSurvey.owner == outputSurvey.owner)
                    "Cannot sell survey key to yourself." using (inputSurveyKey.owner == outputSurveyKey.owner)
                    "All of the survey participants must be signers." using (command.signers.containsAll(inputSurvey.participants.map { it.owningKey }))
                    "The cash owner must be signer." using (command.signers.containsAll(inputCash.participants.map { it.owningKey }))
                }
            }
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Issue : Commands
        class Trade : Commands
        class IssueRequest : Commands
        class SendKey: Commands
    }
}

// *********
// * State *
// *********
data class SurveyState(val issuer: Party,
                       val owner: Party,
                       val propertyAddress: String,
                       val landTitleId: String,
                       val surveyDate: String,
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
                              override val linearId: UniqueIdentifier) : LinearState {
    override val participants: List<AbstractParty> get() = listOf(requester)
}