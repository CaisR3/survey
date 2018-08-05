package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import com.survey.Helper.getSurveyByLinearId
import com.survey.SurveyContract
import com.survey.SurveyContract.Companion.SURVEY_CONTRACT_ID
import com.survey.SurveyState
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash


object TradeFlow{

    // *************
    // * Trade flow *
    // The mechanism for trading a survey for payment
    // *************
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val surveyLinearId: UniqueIdentifier,
                    private val newOwner: Party
                    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call() : SignedTransaction {
            // Stage 1. Retrieve survey specified by linearId from the vault.
            progressTracker.currentStep = GENERATING_TRANSACTION
            val surveyToTransfer = getSurveyByLinearId(surveyLinearId, serviceHub)
            val inputSurvey = surveyToTransfer.state.data
            val transferredSurvey = createOutputSurvey(inputSurvey)

            // Stage 2. This flow can only be initiated by the current survey owner.
            val surveyIdentity = inputSurvey.owner
            check(surveyIdentity == ourIdentity) {
                throw FlowException("Survey trade must be initiated by the owner.")
            }

            // Stage 3. Create a trade command.
            val tradeCommand = Command(
                    SurveyContract.Commands.Trade(),
                    inputSurvey.participants.map { it.owningKey })

            // Stage 4. Create a transaction builder. Add the trade command and input survey.
            val firstNotary = serviceHub.networkMapCache.notaryIdentities.first()
            progressTracker.currentStep = BUILDING_TRANSACTION
            val builder = TransactionBuilder(firstNotary)
                    .addInputState(surveyToTransfer)
                    .addOutputState(transferredSurvey, SURVEY_CONTRACT_ID)
                    .addCommand(tradeCommand)

            // Not sure whether the below is correct
            val (_, cashSigningKeys) = Cash.generateSpend(serviceHub, builder, inputSurvey.resalePrice.POUNDS, newOwner)

            // Stage 5. Verify the transaction.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            builder.verify(serviceHub)

            // Stage 6. Sign the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val ptx = serviceHub.signInitialTransaction(builder, inputSurvey.owner.owningKey)

            // Stage 7. Get counterparty signature.
            progressTracker.currentStep = GATHERING_SIGS
            val session = initiateFlow(newOwner)
            subFlow(IdentitySyncFlow.Send(session, ptx.tx))
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    cashSigningKeys + inputSurvey.owner.owningKey,
                    GATHERING_SIGS.childProgressTracker())
            )

            // Stage 8. Finalize the transaction.
            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(stx, FINALISING_TRANSACTION.childProgressTracker()))
        }


        @Suspendable
        private fun createOutputSurvey(inputSurvey: SurveyState): SurveyState {
            return inputSurvey.copy(owner = newOwner)
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
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val otherFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            subFlow(IdentitySyncFlow.Receive(otherFlow))
            val stx = subFlow(SignTxFlowNoChecking(otherFlow))
            return waitForLedgerCommit(stx.id)
        }
    }
}

internal class SignTxFlowNoChecking(otherFlow: FlowSession) : SignTransactionFlow(otherFlow) {
    override fun checkTransaction(stx: SignedTransaction) {
        // TODO: Add checking here.
    }
}