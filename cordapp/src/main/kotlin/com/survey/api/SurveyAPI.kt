package com.survey.api

import net.corda.core.messaging.CordaRPCOps
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
    // Accessible at /api/survey/surveyGetEndpoint.
    @GET
    @Path("surveyGetEndpoint")
    @Produces(MediaType.APPLICATION_JSON)
    fun templateGetEndpoint(): Response {
        return Response.ok("Survey GET endpoint.").build()
    }
}