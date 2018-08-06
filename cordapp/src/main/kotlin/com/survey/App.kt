package com.survey

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.serialization.SerializationWhitelist


// Serialization whitelist.
class TemplateSerializationWhitelist : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(TemplateData::class.java,
            javax.crypto.spec.SecretKeySpec::class.java,
            net.corda.core.transactions.TransactionBuilder::class.java)
}

// This class is not annotated with @CordaSerializable, so it must be added to the serialization whitelist, above, if
// we want to send it to other nodes within a flow.
data class TemplateData(val payload: String)
