package com.survey

import com.google.common.collect.ImmutableList
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import java.security.Key
import java.util.jar.JarInputStream

fun ServiceHub.firstIdentityByName(name: CordaX500Name) = networkMapCache.getNodeByLegalName(name)?.legalIdentities?.first()
        ?: throw IllegalArgumentException("Requested oracle $name not found on network.")

public object Helper {

    fun getSurveyByLinearId(linearId: UniqueIdentifier, serviceHub: ServiceHub): StateAndRef<SurveyState> {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                null,
                ImmutableList.of(linearId),
                Vault.StateStatus.UNCONSUMED, null)
        return serviceHub.vaultService.queryBy<SurveyState>(queryCriteria).states.singleOrNull()
                ?: throw FlowException("Survey with id $linearId not found.")
    }

    fun getSurveyRequestByLinearId(linearId: UniqueIdentifier, serviceHub: ServiceHub): StateAndRef<SurveyRequestState> {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                null,
                ImmutableList.of(linearId),
                Vault.StateStatus.UNCONSUMED, null)
        return serviceHub.vaultService.queryBy<SurveyRequestState>(queryCriteria).states.singleOrNull()
                ?: throw FlowException("Survey with id $linearId not found.")
    }

    fun encryptAttachment(attachment: JarInputStream): Pair<ByteArray?, Key?> {
        return Pair(null, null)
    }
}