package com.survey


import com.survey.flows.RequestSurveyFlow
import com.template.flow.SelfIssueCashFlow
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FlowTests {

    private val network = MockNetwork(listOf("com.survey", "net.corda.finance.contracts.asset"))
    private val a = network.createNode()
    private val b = network.createNode()

    private val landTitleId = "mock_land_title_id"

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(RequestSurveyFlow.Responder::class.java)
        }
    }

    @Before
    fun setup() {
        network.runNetwork()
        // give each node cash
        val issueCashFlow = SelfIssueCashFlow()
        a.startFlow(issueCashFlow).getOrThrow()
        b.startFlow(issueCashFlow).getOrThrow()
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

            // A single game state output
            assertEquals(1, ltx.outputsOfType<SurveyRequestState>().size)

            // One command.
            assertEquals(1, ltx.commands.size)

            val playCmd = ltx.commandsOfType<SurveyContract.Commands.IssueRequest>().single()
            assert(playCmd.signers.containsAll(listOf(a.info.legalIdentities.first().owningKey, b.info.legalIdentities.first().owningKey)))
        }
    }
}