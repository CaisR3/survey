package com.survey.flows

import co.paralleluniverse.fibers.Suspendable
import com.survey.CreateJar
import com.survey.Helper.encryptAttachment
import com.survey.Helper.getSurveyRequestByLinearId
import com.survey.SurveyContract
import com.survey.SurveyContract.Companion.SURVEY_CONTRACT_ID
import com.survey.SurveyState
import com.survey.firstIdentityByName
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import org.apache.commons.io.FileUtils
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream


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
    class IssueSurveyFlow(val linearId: UniqueIdentifier, val attachmentHash: SecureHash) : FlowLogic<SignedTransaction>() {
        val ORACLE_NAME = CordaX500Name("Oracle", "London", "GB")

        @Suspendable
        override fun call(): SignedTransaction {

            val requestState = getSurveyRequestByLinearId(linearId, serviceHub)

            // Reference to transaction notary
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Identity of the requester
            val requester = requestState.state.data.requester

            // Step 1 :  generate unsigned transaction
            val attachment = serviceHub.attachments.openAttachment(attachmentHash)!!.open()

            // Encrypt and store new encrypted attachment
            val result = encryptAttachment(attachment)
            val fileName = "encryptedReport"
            FileUtils.writeByteArrayToFile(File(fileName), result.first!!)

            //Create jar from encrypted file
            CreateJar().run(fileName)
            val hashOfEncryptedAttachment = serviceHub.attachments.importAttachment(FileInputStream(File(fileName + ".jar")))

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

            val surveyRequestUpdated = requestState.state.data.copy(status = "complete")

            val txCommand = Command(SurveyContract.Commands.Issue(), survey.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(requestState)
                    .addOutputState(survey, SURVEY_CONTRACT_ID)
                    .addOutputState(surveyRequestUpdated, SURVEY_CONTRACT_ID)
                    .addAttachment(hashOfEncryptedAttachment)
                    .addCommand(txCommand)

            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3. self sign the transaction
            val ptx = serviceHub.signInitialTransaction(txBuilder)

            val buyerSession = initiateFlow(requester)
            val stx = subFlow(CollectSignaturesFlow(ptx, setOf(buyerSession)))

            // Stage 4. Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(stx))
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

    @InitiatedBy(IssueFlow.IssueSurveyFlow::class)
    class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
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