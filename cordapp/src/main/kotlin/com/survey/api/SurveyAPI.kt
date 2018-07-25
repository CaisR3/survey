package com.survey.api

import com.template.SurveyState
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.IdentityService
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

// *****************
// * API Endpoints *
// *****************
@Path("survey")
class SurveyAPI(val rpcOps: CordaRPCOps) {

    private val myLegalName : CordaX500Name = rpcOps.nodeInfo().legalIdentities.first().name
    val SERVICE_NAMES = listOf("Notary", "Network Map Service")

    /**
     * Test Endpoint accessible at /api/survey/test
     */
    @GET
    @Path("test")
    @Produces(MediaType.APPLICATION_JSON)
    fun templateGetEndpoint(): Response {
        return Response.ok("Survey GET endpoint.").build()
    }

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = rpcOps.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    /**
     * Displays all Survey states that exist in the node's vault.
     */
    @GET
    @Path("surveys")
    @Produces(MediaType.APPLICATION_JSON)
    fun getSurveys() = rpcOps.vaultQueryBy<SurveyState>().states

}