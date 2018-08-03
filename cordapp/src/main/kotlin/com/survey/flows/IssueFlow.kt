package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import com.survey.SurveyContract.Companion.SURVEY_CONTRACT_ID
import net.corda.core.flows.*
import net.corda.core.contracts.Command
import net.corda.core.utilities.ProgressTracker
import com.survey.SurveyState
import com.survey.SurveyContract
import com.survey.SurveyKeyState
import com.survey.SurveyRequestState
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.serialize
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import java.util.*
import java.util.Objects.hash


object IssueFlow {

    // *********
    // * Issue flows *
    // Surveyor and Party A enter 1 of 2 transactions
    // 1. The Buyer creates a transaction with a CashState payment for the surveyor.
    // 2. The Surveyor, once the payment is received and the transaction finalised, begins another transaction with the decoding key for the survey attachment (as an issuance state) and the encoded survey itself (contained as its hash)
    // *********

    // Buyer makes a request to get their house Surveyed, sending address and payment
    @InitiatingFlow
    @StartableByRPC
    class SurveyRequestFlow(val surveyor : Party,
                            val propertyAddress: String,
                            val landTitleId: String,
                            val surveyPrice : Int) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            // Reference to transaction notary
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Identity of the requester
            val requester = serviceHub.myInfo.legalIdentities.first()

            // Step 1 :  generate unsigned transaction

            val surveyRequestState = SurveyRequestState(requester,surveyor,propertyAddress,landTitleId, surveyPrice,UniqueIdentifier())

            val txCommand = Command(SurveyContract.Commands.IssueRequest(), surveyRequestState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(surveyRequestState, SURVEY_CONTRACT_ID)
                    .addCommand(txCommand)

            Cash.generateSpend(serviceHub, txBuilder, surveyPrice.POUNDS, serviceHub.myInfo.legalIdentitiesAndCerts.first(), surveyor)

            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3. self sign the transaction
            val selfSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4. Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(selfSignedTx))


        }
    }
    // Surveyor completes the survey, creates a transaction with two states
    // 1. The SurveyState containing Survey meta data, sell price, owner etc
    // 2. The SurveyIssuanceState which contains the encoded survey's key and it's hash
    @InitiatingFlow
    @StartableByRPC
    class SurveyIssuanceFlow(val purchaser : Party,
                             val surveyDate: String,
                             val initialPrice : Int,
                             val propertyAddress: String,
                             val landTitleId: String,
                             val encodedSurveyHash : String,
                             val encodedSurveyKey : String) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {

            // Obtain a reference to the notary we want to use
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Identity of issuer of Survey (surveyor)
            val surveyor = serviceHub.myInfo.legalIdentities.first()

            // Find attachment, encode it and compute it's hash
            val qCriteria : queryCriteria = Que
            val attachment = serviceHub.vaultService.queryBy()
            val secureHash : SecureHash = SecureHash.parse(encodedSurveyHash)

            // Create SurveyState and SurveyKeyState
            // Issuance therefore in this case intialPrice == resalePrice
            val surveyState = SurveyState(surveyor, purchaser, propertyAddress, landTitleId, surveyDate, initialPrice, initialPrice, UniqueIdentifier())
            val surveyKeyState = SurveyKeyState(surveyor, purchaser, encodedSurveyHash, encodedSurveyKey ,UniqueIdentifier())

            val txCommand = Command(SurveyContract.Commands.Issue(), surveyState.participants.map { it.owningKey })

            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(surveyState, SURVEY_CONTRACT_ID)
                    .addOutputState(surveyKeyState, SURVEY_CONTRACT_ID)
                    .addCommand(txCommand)
                    .addAttachment(secureHash)


            // Stage 2. verify transaction validity
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3. self sign the transaction
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Issuer signs the transaction.
            val selfSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4. finalise transaction
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(selfSignedTx))

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
