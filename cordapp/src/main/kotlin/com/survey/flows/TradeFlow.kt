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
import com.survey.SurveyKeyState
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
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
                    private val surveyKeyLinearId: UniqueIdentifier,
                    private val newOwner: Party
                    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call() : SignedTransaction {
            // Stage 1. Retrieve survey and survey key specified by linearIds from the vault.
            progressTracker.currentStep = GENERATING_TRANSACTION
            val surveyToTransfer = getSurveyByLinearId(surveyLinearId)
            val surveyKeyToTransfer = getSurveyKeyByLinearId(surveyKeyLinearId)
            val inputSurvey = surveyToTransfer.state.data
            val inputSurveyKey = surveyKeyToTransfer.state.data
            val transferredSurvey = createOutputSurvey(inputSurvey)
            val transferredSurveyKey = createOutputSurveyKey(inputSurveyKey)

            // Stage 2. This flow can only be initiated by the current survey owner.
            val surveyIdentity = inputSurvey.owner
            check(surveyIdentity == ourIdentity) {
                throw FlowException("Survey trade must be initiated by the borrower.")
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
                    .addInputState(surveyKeyToTransfer)
                    .addOutputState(transferredSurvey, SURVEY_CONTRACT_ID)
                    .addOutputState(transferredSurveyKey, SURVEY_CONTRACT_ID)
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

            // Stage 11. Finalize the transaction.
            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(stx, FINALISING_TRANSACTION.childProgressTracker()))
        }

        private fun getSurveyByLinearId(linearId: UniqueIdentifier): StateAndRef<SurveyState> {
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                    null,
                    ImmutableList.of(linearId),
                    Vault.StateStatus.UNCONSUMED, null)
            return serviceHub.vaultService.queryBy<SurveyState>(queryCriteria).states.singleOrNull()
                    ?: throw FlowException("Survey with id $linearId not found.")
        }

        private fun getSurveyKeyByLinearId(linearId: UniqueIdentifier): StateAndRef<SurveyKeyState> {
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                    null,
                    ImmutableList.of(linearId),
                    Vault.StateStatus.UNCONSUMED, null)
            return serviceHub.vaultService.queryBy<SurveyKeyState>(queryCriteria).states.singleOrNull()
                    ?: throw FlowException("Survey key with id $linearId not found.")
        }

        @Suspendable
        private fun createOutputSurvey(inputSurvey: SurveyState): SurveyState {
            return inputSurvey.copy(owner = newOwner)
        }

        @Suspendable
        private fun createOutputSurveyKey(inputSurveyKey: SurveyKeyState): SurveyKeyState {
            return inputSurveyKey.copy(owner = newOwner)
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