package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import com.sun.org.apache.xpath.internal.operations.Bool
import com.survey.*
import com.survey.Helper.encryptAttachment
import com.survey.Helper.getSurveyByLinearId
import com.survey.Helper.getSurveyRequestByLinearId
import com.survey.SurveyContract.Companion.SURVEY_CONTRACT_ID
import net.corda.core.flows.*
import net.corda.core.contracts.Command
import net.corda.core.utilities.ProgressTracker
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.serialize
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import java.security.Key
import java.util.*
import java.util.Objects.hash
import javax.crypto.KeyGenerator


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
    class IssueSurveyFlow(val linearId: UniqueIdentifier, val attachmentHash: SecureHash) : FlowLogic<Unit>() {
        val ORACLE_NAME = CordaX500Name("Oracle", "London", "GB")

        @Suspendable
        override fun call() {

            val requestState = getSurveyRequestByLinearId(linearId, serviceHub)

            // Reference to transaction notary
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Identity of the requester
            val requester = requestState.state.data.requester

            // Step 1 :  generate unsigned transaction
            val attachment = serviceHub.attachments.openAttachment(attachmentHash)!!.openAsJAR()

            // TODO here
            val result = encryptAttachment(attachment)
            val hashOfEncryptedAttachment = SecureHash.sha256(result.first!!)


            val survey: SurveyState = SurveyState(
                    ourIdentity, requester, requestState.state.data.landTitleId,
                    requestState.state.data.surveyPrice, requestState.state.data.surveyPrice,
                    hashOfEncryptedAttachment, linearId)


            val oracle = serviceHub.firstIdentityByName(ORACLE_NAME)
            val session = initiateFlow(oracle)
            val response = session.sendAndReceive<Boolean>(Pair(hashOfEncryptedAttachment, result.second))

            if (!response.unwrap { it }) {
                throw FlowException("The Oracle rejected the key.")
            }

            val surveyRequestUpdated = requestState.state.data.copy(status = "Complete")

            val txCommand = Command(SurveyContract.Commands.Issue(), survey.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(requestState)
                    .addOutputState(survey, SURVEY_CONTRACT_ID)
                    .addOutputState(surveyRequestUpdated, SURVEY_CONTRACT_ID)
                    .addCommand(txCommand)

            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3. self sign the transaction
            val selfSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4. Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(selfSignedTx))
        }

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