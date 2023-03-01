package com.smallcloud.codify.utils

import com.google.gson.*
import com.smallcloud.codify.struct.SupportLanguages
import java.lang.reflect.Type

class BooleanSerializer : JsonDeserializer<Boolean?> {

    @Throws(JsonParseException::class)
    override fun deserialize(arg0: JsonElement, arg1: Type?, arg2: JsonDeserializationContext?): Boolean {
        return arg0.asInt > 0
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