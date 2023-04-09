package de.mm20.launcher2.ktx

import java.nio.ByteBuffer
import java.util.UUID

fun UUID.toBytes(): ByteArray {
    val bytes = ByteArray(16)
    val buffer = ByteBuffer.wrap(bytes)
    buffer.putLong(mostSignificantBits)
    buffer.putLong(leastSignificantBits)
    return buffer.array()
}