package de.mm20.launcher2.themes

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic

internal class ColorRefSerializer: KSerializer<ColorRef> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("$", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ColorRef {
        return Color(decoder.decodeString()) as ColorRef
    }

    override fun serialize(encoder: Encoder, value: ColorRef) {
        encoder.encodeString(value.toString())
    }
}

internal class StaticColorSerializer: KSerializer<StaticColor> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("#", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): StaticColor {
        return Color(decoder.decodeString()) as StaticColor
    }

    override fun serialize(encoder: Encoder, value: StaticColor) {
        encoder.encodeString(value.toString())
    }
}

internal val module = SerializersModule {
    polymorphic(Color::class) {
        subclass(ColorRef::class, ColorRefSerializer())
        subclass(StaticColor::class, StaticColorSerializer())
    }

}

val ThemeJson = Json {
    serializersModule = module
    useArrayPolymorphism = true
}

fun Theme.toJson(): String {
    return ThemeJson.encodeToString(this)
}

fun Theme.Companion.fromJson(json: String): Theme {
    return ThemeJson.decodeFromString(json)
}