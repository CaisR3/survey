package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import com.survey.SurveyContract.Companion.SURVEY_CONTRACT_ID
import net.corda.core.flows.*
import net.corda.core.contracts.Command
import net.corda.core.utilities.ProgressTracker
import com.survey.SurveyState
import com.survey.SurveyContract
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder


object TradeFlow{

    // *************
    // * Trade flow *
    // The mechanism for trading a survey for payment
    // *************
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val surveyRef : StateRef,
                    val propertyAddress: String,
                    val landTitleId: String,
                    val surveyDate: String,
                    val issuanceDate: String,
                    val expiryDate: String,
                    val initialPrice: Int,
                    val resalePrice: Int,
                    val newOwner : Party) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {

        }

        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        override val progressTracker = tracker()

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }
    }
}
