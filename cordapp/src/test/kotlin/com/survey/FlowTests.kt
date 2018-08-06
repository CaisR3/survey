package com.survey


import com.survey.flows.*
import com.template.flow.SelfIssueCashFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.AttachmentId
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import kotlin.test.assertEquals

class FlowTests {

    private val network = MockNetwork(listOf("com.survey", "net.corda.finance.contracts.asset"))
    private val a = network.createNode()
    private val b = network.createNode() // Issuer
    private val c = network.createNode() // New buyer

    val ORACLE_NAME = CordaX500Name("Oracle", "London", "GB")
    private val o = network.createNode(ORACLE_NAME)

    private val landTitleId = "mock_land_title_id"

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(RequestSurveyFlow.Responder::class.java)
            it.registerInitiatedFlow(IssueFlow.Acceptor::class.java)
            it.registerInitiatedFlow((TradeBuildingFlow.Responder::class.java))
        }
        o.registerInitiatedFlow(GiveOracleKey::class.java)
    }

    @Before
    fun setup() {
        network.runNetwork()
        // give each node cash
        val issueCashFlow = SelfIssueCashFlow()
        a.startFlow(issueCashFlow).getOrThrow()
        b.startFlow(issueCashFlow).getOrThrow()
        c.startFlow(issueCashFlow).getOrThrow()
        network.runNetwork()
    }

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `Request survey flow works`() {
        // create game first
        val createFlow = RequestSurveyFlow.Initiator(b.info.legalIdentities.first(), landTitleId, 1000)
        val createFuture = a.startFlow(createFlow)
        network.runNetwork()
        val stx = createFuture.getOrThrow()

        // We check the recorded transaction in both vaults.
        listOf(a, b).forEach { node ->
            assertEquals(stx, node.services.validatedTransactions.getTransaction(stx.id))

            val ltx = node.transaction {
                stx.toLedgerTransaction(node.services)
            }

            // A single survey state output
            assertEquals(1, ltx.outputsOfType<SurveyRequestState>().size)

            // One command.
            assertEquals(2, ltx.commands.size)

            val issueCmd = ltx.commandsOfType<SurveyContract.Commands.IssueRequest>().single()
            assert(issueCmd.signers.containsAll(listOf(a.info.legalIdentities.first().owningKey, b.info.legalIdentities.first().owningKey)))
        }
    }

    @Test
    fun `Issue survey flow works`() {
        // Request survey first
        val createFlow = RequestSurveyFlow.Initiator(b.info.legalIdentities.first(), landTitleId, 1000)
        val createFuture = a.startFlow(createFlow)
        network.runNetwork()
        val stx1 = createFuture.getOrThrow()

        // Create survey attachment

        val surveyRequestState = stx1.coreTransaction.getOutput(0) as SurveyRequestState

        val file = File("report.jar")
        val inputStream = FileInputStream(file)
        var attachmentHash: AttachmentId? = null
        a.transaction {
            attachmentHash = b.services.attachments.importAttachment(inputStream)
        }
        val issueFlow = IssueFlow.IssueSurveyFlow(surveyRequestState.linearId, attachmentHash!!)
        val issueFuture = a.startFlow(issueFlow)
        network.runNetwork()
        val stx2 = issueFuture.getOrThrow()

        // We check the recorded transaction in both vaults.
        listOf(a, b).forEach { node ->
            assertEquals(stx2, node.services.validatedTransactions.getTransaction(stx2.id))

            val ltx = node.transaction {
                stx2.toLedgerTransaction(node.services)
            }

            // A single survey request state output
            assertEquals(1, ltx.outputsOfType<SurveyRequestState>().size)

            // A single survey state output
            assertEquals(1, ltx.outputsOfType<SurveyState>().size)

            // One command.
            assertEquals(1, ltx.commands.size)
        }
    }

    @Test
    fun `Trade survey flow works`() {
        // Request survey first
        val createFlow = RequestSurveyFlow.Initiator(b.info.legalIdentities.first(), landTitleId, 1000)
        val createFuture = a.startFlow(createFlow)
        network.runNetwork()
        val stx1 = createFuture.getOrThrow()

        // Create survey attachment
        val surveyRequestState = stx1.coreTransaction.getOutput(0) as SurveyRequestState
        val surveyId = surveyRequestState.linearId


        val file = File("report.jar")
        val inputStream = FileInputStream(file)
        var attachmentHash: AttachmentId? = null
        a.transaction {
            attachmentHash = b.services.attachments.importAttachment(inputStream)
        }
        val issueFlow = IssueFlow.IssueSurveyFlow(surveyId, attachmentHash!!)
        val issueFuture = a.startFlow(issueFlow)
        network.runNetwork()
        val stx2 = issueFuture.getOrThrow()


        // Try and trade
        val tradeFlow = TradeFlow.Initiator(surveyId, c.info.legalIdentities.first())
        val tradeFuture = a.startFlow(tradeFlow)
        network.runNetwork()
        val stx3 = tradeFuture.getOrThrow()



        // We check the recorded transaction in both vaults.
        listOf(a, b).forEach { node ->
            assertEquals(stx2, node.services.validatedTransactions.getTransaction(stx2.id))

            val ltx = node.transaction {
                stx2.toLedgerTransaction(node.services)
            }

            // A single survey request state output
            assertEquals(1, ltx.outputsOfType<SurveyRequestState>().size)

            // A single survey state output
            assertEquals(1, ltx.outputsOfType<SurveyState>().size)

            // One command.
            assertEquals(1, ltx.commands.size)
        }
    }
}