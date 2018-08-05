package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import com.google.common.collect.ImmutableList
import com.survey.SurveyContract.Companion.SURVEY_CONTRACT_ID
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.contracts.Command
import net.corda.core.utilities.ProgressTracker
import com.survey.SurveyContract
import com.survey.SurveyKeyState
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria


object SendKeyFlow{

    // *************
    // * Send key flow *
    // The mechanism for sending a survey key
    // *************
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val keyLinearId: UniqueIdentifier
                    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call() : SignedTransaction {
            // Stage 1. Retrieve survey key specified by linearId from the vault.
            progressTracker.currentStep = GENERATING_TRANSACTION
            val surveyKeyToSend = getKeyByLinearId(keyLinearId)
            val inputSurveyKey = surveyKeyToSend.state.data
            val sentSurveyKey = createOutputSurveyKey(inputSurveyKey)

            // Stage 2 Create a send key command.
            val sendKeyCommand = Command(
                    SurveyContract.Commands.SendKey(),
                    inputSurveyKey.participants.map { it.owningKey })

            // Stage 3. Create a transaction builder. Add the trade command and input survey key.
            val firstNotary = serviceHub.networkMapCache.notaryIdentities.first()
            progressTracker.currentStep = BUILDING_TRANSACTION
            val builder = TransactionBuilder(firstNotary)
                    .addInputState(surveyKeyToSend)
                    .addOutputState(sentSurveyKey, SURVEY_CONTRACT_ID)
                    .addCommand(sendKeyCommand)

            // Stage 4. Verify the transaction.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            builder.verify(serviceHub)

            // Stage 6. Sign the transaction.
            progressTracker.currentStep = TradeFlow.Initiator.Companion.SIGNING_TRANSACTION
            val stx = serviceHub.signInitialTransaction(builder, ourIdentity.owningKey)

            // Stage 5. Finalize the transaction.
            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(stx, FINALISING_TRANSACTION.childProgressTracker()))
        }

        private fun getKeyByLinearId(linearId: UniqueIdentifier): StateAndRef<SurveyKeyState> {
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                    null,
                    ImmutableList.of(linearId),
                    Vault.StateStatus.UNCONSUMED, null)
            return serviceHub.vaultService.queryBy<SurveyKeyState>(queryCriteria).states.singleOrNull()
                    ?: throw FlowException("Survey key with id $linearId not found.")
        }

        @Suspendable
        private fun createOutputSurveyKey(inputSurveyKey: SurveyKeyState): SurveyKeyState {
            return inputSurveyKey.copy()
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