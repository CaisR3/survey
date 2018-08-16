package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import com.survey.Helper
import com.survey.SurveyContract
import com.survey.SurveyState
import com.survey.firstIdentityByName
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import java.security.Key


object RequestKeyFlow {

    // *************
    // RequestKeyFlow - beginning of the communication between buyer and oracle
    // Initiator : retrieves survey from buyer's vault, fetches oracle's identity,
    // initiates session, sends proof of ownership via ownership detailed through state
    // *************
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val surveyLinearId: UniqueIdentifier) : FlowLogic<Unit>() {

        val ORACLE_NAME = CordaX500Name("Oracle", "London", "US")

        @Suspendable
        override fun call() : Unit {

            val surveyToTransfer = Helper.getSurveyByLinearId(surveyLinearId, serviceHub)
            val oracle = serviceHub.firstIdentityByName(ORACLE_NAME)
            val keyTradeCommand = Command(
                    SurveyContract.Commands.Trade(),
                    listOf(surveyToTransfer.state.data.owner.owningKey, oracle.owningKey))

            val firstNotary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(firstNotary)
                    .addInputState(surveyToTransfer)
                    .addCommand(keyTradeCommand)
                    .addAttachment(surveyToTransfer.state.data.surveyHash)

            val session = initiateFlow(oracle)
            //val key = session.sendAndReceive<TransactionBuilder>()
        }
    }
}
