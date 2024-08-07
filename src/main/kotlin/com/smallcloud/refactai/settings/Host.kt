package com.smallcloud.refactai.settings

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

enum class HostKind(val value: String) {
    CLOUD("cloud"),
    SELF("self"),
    ENTERPRISE("enterprise"),
}

sealed class Host {
    data class CloudHost(val apiKey: String, val sendCorrectedCodeSnippets: Boolean) : Host()

    data class SelfHost(val endpointAddress: String) : Host()

    data class Enterprise(val endpointAddress: String, val apiKey: String) : Host()
}

class HostDeserializer : JsonDeserializer<Host> {
    override fun deserialize(p0: JsonElement?, p1: Type?, p2: JsonDeserializationContext?): Host? {
        val host = p0?.asJsonObject?.get("host")?.asJsonObject
        val type = host?.get("type")?.asString

        return when (type) {
            HostKind.CLOUD.value -> {
                val apiKey = host.get("apiKey")?.asString ?: return null
                val sendCorrectedCodeSnippets = host.get("sendCorrectedCodeSnippets")?.asBoolean?: false
                return Host.CloudHost(apiKey, sendCorrectedCodeSnippets)
            }
            HostKind.SELF.value -> {
                val endpointAddress = host.get("endpointAddress")?.asString ?: return null
                return Host.SelfHost(endpointAddress)
            }
            HostKind.ENTERPRISE.value -> {
                val endpointAddress = host.get("endpointAddress")?.asString ?: return null
                val apiKey = host.get("apiKey")?.asString ?: return null
                return Host.Enterprise(endpointAddress, apiKey)
            }
            else -> null
        }
    }
}
