package com.survey


import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test

class FlowTests {

    private val network = MockNetwork(listOf("com.survey"))
    private val a = network.createNode()
    private val b = network.createNode()

    init {
        listOf(a, b).forEach {

        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `dummy test`() {

    }
}