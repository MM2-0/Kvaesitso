package de.mm20.launcher2.keepasstl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object SteganographyEngine {

    /**
     * Embeds a byte array into a Bitmap using high-performance batch Least Significant Bit (LSB) steganography.
     * Operates directly on native IntArray buffers (avoiding slow JNI getPixel/setPixel calls).
     * The first 4 bytes of the hidden payload represent the length of the data (Big Endian).
     */
    fun embedInImage(sourceImage: File, payload: ByteArray, outputImage: File): Boolean {
        val bitmap = BitmapFactory.decodeFile(sourceImage.absolutePath) ?: return false
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        // We need 4 bytes for length + payload size
        val totalBytes = 4 + payload.size
        val totalBits = totalBytes * 8

        // Ensure image is large enough (we hide 3 bits per pixel: R, G, B)
        val maxBits = totalPixels * 3
        if (totalBits > maxBits) {
            bitmap.recycle()
            return false
        }

        // Fast batch pixel buffer
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val dataWithLength = ByteArray(totalBytes)
        // Store size in first 4 bytes (Big Endian)
        dataWithLength[0] = (payload.size shr 24).toByte()
        dataWithLength[1] = (payload.size shr 16).toByte()
        dataWithLength[2] = (payload.size shr 8).toByte()
        dataWithLength[3] = payload.size.toByte()
        System.arraycopy(payload, 0, dataWithLength, 4, payload.size)

        var bitIndex = 0
        var pixelIndex = 0

        while (bitIndex < totalBits && pixelIndex < totalPixels) {
            val pixel = pixels[pixelIndex]
            val a = (pixel shr 24) and 0xFF
            var r = (pixel shr 16) and 0xFF
            var g = (pixel shr 8) and 0xFF
            var b = pixel and 0xFF

            // Red LSB
            if (bitIndex < totalBits) {
                val bit = getBit(dataWithLength, bitIndex)
                r = (r and 0xFE) or bit
                bitIndex++
            }
            // Green LSB
            if (bitIndex < totalBits) {
                val bit = getBit(dataWithLength, bitIndex)
                g = (g and 0xFE) or bit
                bitIndex++
            }
            // Blue LSB
            if (bitIndex < totalBits) {
                val bit = getBit(dataWithLength, bitIndex)
                b = (b and 0xFE) or bit
                bitIndex++
            }

            pixels[pixelIndex] = (a shl 24) or (r shl 16) or (g shl 8) or b
            pixelIndex++
        }

        val mutableBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        mutableBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        FileOutputStream(outputImage).use { out ->
            mutableBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        mutableBitmap.recycle()
        bitmap.recycle()
        return true
    }

    /**
     * Extracts a hidden payload from an LSB encoded Bitmap using batch pixel processing.
     */
    fun extractFromImage(sourceImage: File): ByteArray? {
        val bitmap = BitmapFactory.decodeFile(sourceImage.absolutePath) ?: return null
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        // Read length (32 bits = 4 bytes)
        var length = 0
        var lengthBits = 0
        var pixelIndex = 0

        while (lengthBits < 32 && pixelIndex < totalPixels) {
            val pixel = pixels[pixelIndex]
            val r = (pixel shr 16) and 1
            val g = (pixel shr 8) and 1
            val b = pixel and 1

            if (lengthBits < 32) { length = (length shl 1) or r; lengthBits++ }
            if (lengthBits < 32) { length = (length shl 1) or g; lengthBits++ }
            if (lengthBits < 32) { length = (length shl 1) or b; lengthBits++ }
            pixelIndex++
        }

        // Sanity & safety check (max 50MB payload to avoid OOM attack)
        if (length <= 0 || length > 50 * 1024 * 1024) return null

        val targetBits = (4 + length) * 8
        if (targetBits > totalPixels * 3) return null

        val payload = ByteArray(length)
        var totalBitsRead = 0
        var payloadByteIndex = 0
        var currentByte = 0
        var bitsInCurrentByte = 0

        pixelIndex = 0
        while (totalBitsRead < targetBits && pixelIndex < totalPixels) {
            val pixel = pixels[pixelIndex]

            for (shift in 16 downTo 0 step 8) {
                val bit = (pixel shr shift) and 1
                if (totalBitsRead >= 32 && totalBitsRead < targetBits) {
                    currentByte = (currentByte shl 1) or bit
                    bitsInCurrentByte++
                    if (bitsInCurrentByte == 8) {
                        payload[payloadByteIndex++] = currentByte.toByte()
                        currentByte = 0
                        bitsInCurrentByte = 0
                    }
                }
                totalBitsRead++
                if (totalBitsRead >= targetBits) break
            }
            pixelIndex++
        }

        return payload
    }

    private fun getBit(data: ByteArray, bitIndex: Int): Int {
        val byteIndex = bitIndex / 8
        val bitOffset = 7 - (bitIndex % 8)
        return (data[byteIndex].toInt() shr bitOffset) and 1
    }
}
