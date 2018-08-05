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
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import javax.annotation.Signed


object TradeBuildingFlow{

    // *************
    // * Trade flow *
    // The mechanism for trading a survey for payment
    // *************
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val counterPartySession: FlowSession, val txBuilder: TransactionBuilder) : FlowLogic<SignedTransaction>() {

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

        @Suspendable
        override fun call(): SignedTransaction {
            return counterPartySession.sendAndReceive<SignedTransaction>(txBuilder).unwrap { it }
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val otherFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val builder = otherFlow.receive<TransactionBuilder>().unwrap { it }
            val surveyState = builder.outputStates().first().data as SurveyState
            if(surveyState.owner == ourIdentity) {
                Cash.generateSpend(serviceHub, builder, (surveyState.resalePrice * 0.8).POUNDS, otherFlow.counterparty)
                Cash.generateSpend(serviceHub, builder, (surveyState.resalePrice * 0.2).POUNDS, surveyState.issuer)
            } else {
                throw FlowException("We're not the new owner")
            }

            builder.verify(serviceHub)

            val ptx = serviceHub.signInitialTransaction(builder)

            return ptx
        }
    }
}