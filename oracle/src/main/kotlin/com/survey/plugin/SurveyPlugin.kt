package com.survey.plugin

import net.corda.core.messaging.CordaRPCOps
import net.corda.webserver.services.WebServerPluginRegistry
import com.survey.api.SurveyAPI
import java.util.function.Function

// ***********
// * Plugins *
// ***********
class SurveyWebPlugin : WebServerPluginRegistry {
    // A list of lambdas that create objects exposing web JAX-RS REST APIs.
    override val webApis: List<java.util.function.Function<CordaRPCOps, out Any>> = listOf(Function(::SurveyAPI))
    // A list of directories in the resources directory that will be served by Jetty under /web.
    // This template's web frontend is accessible at /web/template.
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the surveyWeb directory in resources to /web/survey
            "survey" to javaClass.classLoader.getResource("surveyWeb").toExternalForm()
    )
}