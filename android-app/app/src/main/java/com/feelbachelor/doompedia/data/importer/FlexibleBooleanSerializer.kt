package com.feelbachelor.doompedia.data.importer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Accepts booleans encoded as true/false, 1/0, and string equivalents.
 */
object FlexibleBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleBoolean", PrimitiveKind.BOOLEAN)

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }

    override fun deserialize(decoder: Decoder): Boolean {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val element = jsonDecoder.decodeSerializableValue(JsonElement.serializer())
        val primitive = element as? JsonPrimitive
            ?: throw SerializationException("Expected boolean-compatible JSON primitive")

        primitive.booleanOrNull?.let { return it }
        primitive.intOrNull?.let { number ->
            return when (number) {
                0 -> false
                1 -> true
                else -> throw SerializationException("Unsupported numeric boolean value: $number")
            }
        }

        return when (primitive.content.trim().lowercase()) {
            "false", "0", "no", "n" -> false
            "true", "1", "yes", "y" -> true
            else -> throw SerializationException(
                "Unsupported boolean value: '${primitive.content}'"
            )
        }
    }
}
