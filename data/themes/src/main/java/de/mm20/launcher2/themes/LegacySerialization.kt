package de.mm20.launcher2.themes

import de.mm20.launcher2.themes.colors.Color
import de.mm20.launcher2.themes.colors.ColorRef
import de.mm20.launcher2.themes.colors.Colors
import de.mm20.launcher2.themes.colors.StaticColor
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Deprecated("Only used for backwards compatibility with old themes. New themes should use the new serialization format.")
internal class LegacyColorRefSerializer: KSerializer<ColorRef> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("$", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ColorRef {
        return Color.fromString(decoder.decodeString()) as ColorRef
    }

    override fun serialize(encoder: Encoder, value: ColorRef) {
        encoder.encodeString(value.toString())
    }
}

@Deprecated("Only used for backwards compatibility with old themes. New themes should use the new serialization format.")
internal class LegacyStaticColorSerializer: KSerializer<StaticColor> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("#", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): StaticColor {
        return Color.fromString(decoder.decodeString()) as StaticColor
    }

    override fun serialize(encoder: Encoder, value: StaticColor) {
        encoder.encodeString(value.toString())
    }
}

@Deprecated("Only used for backwards compatibility with old themes. New themes should use the new serialization format.")
internal val legacyModule = SerializersModule {
    polymorphic(Color::class) {
        subclass(ColorRef::class, LegacyColorRefSerializer())
        subclass(StaticColor::class, LegacyStaticColorSerializer())
    }

}

@Deprecated("Only used for backwards compatibility with old themes. New themes should use the new serialization format.")
val LegacyThemeJson = Json {
    serializersModule = legacyModule
    useArrayPolymorphism = true
}

@Deprecated("Only used for backwards compatibility with old themes. New themes should use the new serialization format.")
fun Colors.toLegacyJson(): String {
    return LegacyThemeJson.encodeToString(this)
}


@Deprecated("Only used for backwards compatibility with old themes. New themes should use the new serialization format.")
fun Colors.Companion.fromLegacyJson(json: String): Colors {
    return try {
        LegacyThemeJson.decodeFromString(json)
    } catch (e: SerializationException) {
        throw IllegalArgumentException(e)
    }
}