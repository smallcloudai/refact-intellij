package com.smallcloud.refactai.utils

import com.google.gson.*
import com.smallcloud.refactai.struct.SupportLanguages
import java.lang.reflect.Type

class BooleanSerializer : JsonDeserializer<Boolean?> {

    @Throws(JsonParseException::class)
    override fun deserialize(arg0: JsonElement, arg1: Type?, arg2: JsonDeserializationContext?): Boolean {
        val arg0P = (arg0 as JsonPrimitive)
        return if (arg0P.isBoolean) {
            arg0P.asBoolean
        } else {
            arg0P.asInt > 0
        }

    }
}

class RegexSerializer : JsonDeserializer<SupportLanguages?> {
    @Throws(JsonParseException::class)
    override fun deserialize(arg0: JsonElement, arg1: Type?, arg2: JsonDeserializationContext?): SupportLanguages {
        return SupportLanguages(arg0.asString.split(";"))
    }
}



fun makeGson(): Gson {
    val b = GsonBuilder()
    val serializer = BooleanSerializer()
    val serializer1 = RegexSerializer()
    b.registerTypeAdapter(Boolean::class.java, serializer)
    b.registerTypeAdapter(Boolean::class.javaPrimitiveType, serializer)
    b.registerTypeAdapter(SupportLanguages::class.java, serializer1)
    return b.create()
}