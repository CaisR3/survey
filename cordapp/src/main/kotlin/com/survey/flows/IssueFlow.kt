package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import com.survey.SurveyContract.Companion.SURVEY_CONTRACT_ID
import net.corda.core.flows.*
import net.corda.core.contracts.Command
import net.corda.core.utilities.ProgressTracker
import com.survey.SurveyState
import com.survey.SurveyContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.transactions.TransactionBuilder
import java.util.*


object IssueFlow{

    // *********
    // * Issue flow *
    // The mechanism for issuing a survey onto the ledger
    // *********
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val propertyAddress: String,
                    val landTitleId: String,
                    val surveyDate: String,
                    val issuanceDate: String,
                    val expiryDate: String,
                    val initialPrice: Int,
                    val resalePrice: Int) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {

            //Obtain a reference to the notary we want to use
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Identity of issuer of Survey == identity of survey owner
            val issuer = serviceHub.myInfo.legalIdentities.first()
            val owner = serviceHub.myInfo.legalIdentities.first()


            //Step 1 :  generate unsigned transaction
            progressTracker.currentStep = GENERATING_TRANSACTION
            val surveyState = SurveyState(issuer, owner, propertyAddress, landTitleId, surveyDate, issuanceDate, expiryDate, initialPrice, resalePrice, UniqueIdentifier())
            val txCommand = Command(SurveyContract.Commands.Issue(), surveyState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(surveyState, SURVEY_CONTRACT_ID)
                    .addCommand(txCommand)

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Issuer signs the transaction.
            val selfSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS
            // Send the state to the counterparty which in this case is the issuer himself, and receive it back with their signature.
            val otherPartyFlow = initiateFlow(owner)
            val fullySignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx))

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
