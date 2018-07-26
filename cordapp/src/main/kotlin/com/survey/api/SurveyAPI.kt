package com.survey.api

import com.survey.SurveyState
import com.survey.flows.IssueFlow.Initiator
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.getOrThrow
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger

// *****************
// * API Endpoints *
// *****************
@Path("survey")
class SurveyAPI(val rpcOps: CordaRPCOps) {

    private val myLegalName : CordaX500Name = rpcOps.nodeInfo().legalIdentities.first().name
    val SERVICE_NAMES = listOf("Notary", "Network Map Service")
    companion object {
        private val logger: Logger = loggerFor<SurveyAPI>()
    }

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
     * Endpoint to issue survey
     */
    @PUT
    @Path("issue-survey")
    fun issueSurvey(@QueryParam("propertyAddress") propertyAddress : String,
                    @QueryParam("landTitleId") landTitleId : String,
                    @QueryParam("surveyDate") surveyDate: String,
                    @QueryParam("issuanceDate") issuanceDate: String,
                    @QueryParam("expiryDate") expiryDate: String,
                    @QueryParam("initialPrice") initialPrice: Int,
                    @QueryParam("resalePrice") resalePrice: Int) : Response{

        if(initialPrice < 0 ){
            return Response.status(Response.Status.BAD_REQUEST).entity("Initial price cannot be less then zero.\n").build()
        }
        return try {
            //throw IllegalAccessError("")
            val signedTx = rpcOps.startTrackedFlowDynamic(Initiator::class.java, propertyAddress, landTitleId, surveyDate, issuanceDate, expiryDate, initialPrice, resalePrice).returnValue.getOrThrow()
            Response.status(Response.Status.CREATED).entity("Transaction id "+signedTx.hashCode()+" committed to ledger.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.BAD_REQUEST).entity(ex.message!!).build()
        }


    }

    /*
     * Displays all Survey states that exist in the node's vault.
     */
    @GET
    @Path("surveys")
    @Produces(MediaType.APPLICATION_JSON)
    fun getSurveys() = rpcOps.vaultQueryBy<SurveyState>().states

}


