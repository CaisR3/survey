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
    @InitiatedBy(TradeFlow.Initiator::class)
    @StartableByRPC
    class Responder(val counterPartySession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call(): Unit {
            val builder = counterPartySession.receive<TransactionBuilder>().unwrap { it }
            val surveyState = builder.outputStates().first().data as SurveyState
            if(surveyState.owner == ourIdentity) {
                Cash.generateSpend(serviceHub, builder, (surveyState.resalePrice * 0.8).POUNDS, counterPartySession.counterparty)
                Cash.generateSpend(serviceHub, builder, (surveyState.resalePrice * 0.2).POUNDS, surveyState.issuer)
            } else {
                throw FlowException("We're not the new owner")
            }

            builder.verify(serviceHub)

            val ptx = serviceHub.signInitialTransaction(builder)

            counterPartySession.send(ptx)
        }
    }
}