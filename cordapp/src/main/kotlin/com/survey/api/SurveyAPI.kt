package com.survey.api

import com.survey.SurveyState
import com.survey.flows.IssueFlow
import com.survey.flows.RequestSurveyFlow
import com.survey.flows.TradeFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.loggerFor
import net.corda.finance.POUNDS
import net.corda.finance.flows.CashIssueFlow
import org.slf4j.Logger
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response


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

    /*
    * Displays all Survey states that exist in the node's vault.
    */
    @GET
    @Path("surveys")
    @Produces(MediaType.APPLICATION_JSON)
    fun getSurveys() = rpcOps.vaultQueryBy<SurveyState>().states

    /*
  * Displays all Survey states that exist in the node's vault.
  */
    @GET
    @Path("get-key")
    @Produces(MediaType.APPLICATION_JSON)
    fun getKey(surveyId: UniqueIdentifier) {
        val survey = rpcOps.vaultQueryBy<SurveyState>().states.filter { it.state.data.linearId == surveyId }
    }

    /*
    * Upload Attachment Endpoint
    */
    @PUT
    @Path("upload/attachment")
    @Produces(MediaType.APPLICATION_JSON)
    fun uploadAttachment() {
        System.out.println("--------uploading attachment-------")
    }

    /**
     * Endpoint to for a potential buyer to request a Survey from a surveyor
     */
    @PUT
    @Path("survey-request")
    fun surveyRequest(@QueryParam("surveyor") surveyor: String, //e.g O=Surveyor,L=New York,C=US
                    @QueryParam("propertyAddress") propertyAddress : String,
                    @QueryParam("landTitleId") landTitleId : String,
                    @QueryParam("surveyPrice") surveyPrice : Int) : Response{

        if(surveyPrice < 0 ){
            return Response.status(Response.Status.BAD_REQUEST).entity("Survey price cannot be less then zero.\n").build()
        }
        val sx500Name = CordaX500Name.parse(surveyor)
        val surveyorParty = rpcOps.wellKnownPartyFromX500Name(sx500Name) ?: throw Exception("Party not recognised.")
        return try {
            val signedTx = rpcOps.startTrackedFlowDynamic(RequestSurveyFlow.Initiator::class.java, surveyorParty, propertyAddress, landTitleId, surveyPrice).returnValue
            Response.status(Response.Status.CREATED).entity("SurveyRequestFlow success : Transaction id "+signedTx.hashCode()+" committed to ledger.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.BAD_REQUEST).entity(ex.message!!).build()
        }
    }


    /**
     * Endpoint for Surveyor to issue a survey
     */
    @PUT
    @Path("issue-survey")
    fun issueSurvey(@QueryParam("purchaser") purchaser : String,
                    @QueryParam("propertyAddress") propertyAddress : String,
                    @QueryParam("landTitleId") landTitleId : String,
                    @QueryParam("surveyDate") surveyDate: String,
                    @QueryParam("price") price : Int,
                    @QueryParam("encodedSurveyHash") encodedSurveyHash: String,
                    @QueryParam("encodedSurveyKey") encodedSurveyKey: String) : Response{

        if(price < 0){
            return Response.status(Response.Status.BAD_REQUEST).entity("Price cannot be less then zero.\n").build()
        }

        val purchaserx500Name = CordaX500Name.parse(purchaser)
        val purchaserParty = rpcOps.wellKnownPartyFromX500Name(purchaserx500Name) ?: throw Exception("Party not recognised.")

        return try {
            val signedTx = rpcOps.startTrackedFlowDynamic(IssueFlow.IssueSurveyFlow::class.java, purchaserParty, surveyDate, price, propertyAddress, landTitleId, encodedSurveyHash, encodedSurveyKey).returnValue
            Response.status(Response.Status.CREATED).entity("Transaction id "+signedTx.hashCode()+" committed to ledger.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    /**
     * Endpoint for Surveyor to issue a survey
     */
    @PUT
    @Path("trade-survey")
    fun issueSurvey(@QueryParam("purchaser") purchaser : String, @QueryParam("surveyId") surveyId : String) : Response{

        val purchaserx500Name = CordaX500Name.parse(purchaser)
        val purchaserParty = rpcOps.wellKnownPartyFromX500Name(purchaserx500Name) ?: throw Exception("Party not recognised.")
        val uniqueIdentifier = UniqueIdentifier.fromString(surveyId)

        return try {
            val signedTx = rpcOps.startTrackedFlowDynamic(TradeFlow.Initiator::class.java, uniqueIdentifier, purchaserParty).returnValue
            Response.status(Response.Status.CREATED).entity("Transaction id "+signedTx.hashCode()+" committed to ledger.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    @PUT
    @Path("self-issue-cash")
    fun selfIssueCash(): Response {

        // 1. Prepare issue request.
        val issueAmount = 1000000.POUNDS
        val notary = rpcOps.notaryIdentities().firstOrNull() ?: throw IllegalStateException("Could not find a notary.")
        val issueRef = OpaqueBytes.of(0)
        val issueRequest = CashIssueFlow.IssueRequest(issueAmount, issueRef, notary)

        // 2. Start flow and wait for response.
        return try {
            val flowHandle = rpcOps.startFlowDynamic(CashIssueFlow::class.java, issueRequest)
            Response.status(Response.Status.CREATED).entity("Cash Issued Successfully "+flowHandle.hashCode()+".\n").build()

        } catch (e: Exception) {
            Response.status(Response.Status.BAD_REQUEST).entity(e.message!!).build()

        }
    }
}


