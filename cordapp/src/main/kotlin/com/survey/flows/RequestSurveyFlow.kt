package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import com.google.common.collect.ImmutableList
import com.survey.SurveyContract.Companion.SURVEY_CONTRACT_ID
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.contracts.Command
import net.corda.core.utilities.ProgressTracker
import com.survey.SurveyState
import com.survey.SurveyContract
import com.survey.SurveyRequestState
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash


object RequestSurveyFlow{

    // *************
    // * Trade flow *
    // The mechanism for trading a survey for payment
    // *************
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val surveyor: Party,
                    val landTitleId: String,
                    val surveyPrice: Int
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call() : SignedTransaction {
            // Stage 1. Create new survey request.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // How to create a new linearId?
            val outputSurveyRequest = SurveyRequestState(
                    ourIdentity, surveyor, landTitleId, surveyPrice, "pending")

            // Stage 2. This flow can only be initiated by the requester.
            val surveyRequestIdentity = outputSurveyRequest.requester
            check(surveyRequestIdentity == ourIdentity) {
                throw FlowException("Survey request trade must be initiated by the requester.")
            }

            // Stage 3. Create an issue command.
            val issueRequestCommand = Command(
                    SurveyContract.Commands.IssueRequest(),
                    outputSurveyRequest.participants.map { it.owningKey })

            // Stage 4. Create a transaction builder. Add the trade command and input survey.
            val firstNotary = serviceHub.networkMapCache.notaryIdentities.first()
            progressTracker.currentStep = BUILDING_TRANSACTION
            val builder = TransactionBuilder(firstNotary)
                    .addOutputState(outputSurveyRequest, SURVEY_CONTRACT_ID)
                    .addCommand(issueRequestCommand)

            //  This is correct
            Cash.generateSpend(serviceHub, builder, outputSurveyRequest.surveyPrice.POUNDS, outputSurveyRequest.surveyor)

            // Stage 5. Verify the transaction.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            builder.verify(serviceHub)

            // Stage 6. Sign the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val ptx = serviceHub.signInitialTransaction(builder, outputSurveyRequest.requester.owningKey)

            // Stage 7. Get counterparty signature.
            progressTracker.currentStep = GATHERING_SIGS
            val session = initiateFlow(outputSurveyRequest.surveyor)

            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    GATHERING_SIGS.childProgressTracker())
            )

            // Stage 8. Finalize the transaction.
            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(stx, FINALISING_TRANSACTION.childProgressTracker()))
        }

        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        override val progressTracker = tracker()

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction to resell survey.")
            object BUILDING_TRANSACTION : ProgressTracker.Step("Building transaction.")
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
                    BUILDING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    //stx.verify(serviceHub);
                }
            }

            return subFlow(signTransactionFlow)
        }
    }
}