package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import com.survey.Helper
import com.survey.firstIdentityByName
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import java.security.Key


object RequestKeyFlow {

    // *************
    // * Trade flow *
    // The mechanism for trading a survey for payment
    // *************
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val surveyLinearId: UniqueIdentifier
    ) : FlowLogic<Unit>() {

        val ORACLE_NAME = CordaX500Name("Oracle", "London", "US")

        @Suspendable
        override fun call() : Unit {

            val surveyToTransfer = Helper.getSurveyByLinearId(surveyLinearId, serviceHub)
            val inputSurvey = surveyToTransfer.state.data

            val oracle = serviceHub.firstIdentityByName(ORACLE_NAME)
            val session = initiateFlow(oracle)
            val key = session.sendAndReceive<Key>(inputSurvey)
        }
    }
}