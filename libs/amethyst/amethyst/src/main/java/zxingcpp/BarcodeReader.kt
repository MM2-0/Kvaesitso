/*
* Copyright 2021 Axel Waggershauser
*/
// SPDX-License-Identifier: Apache-2.0

// -----------------------------------------------------------------------------
// VENDORED, DO NOT EDIT OR REFORMAT.
//
// Copied verbatim from zxing-cpp v3.1.1:
//   wrappers/android/zxingcpp/src/main/java/zxingcpp/BarcodeReader.kt
//
// We build libzxingcpp_android.so from source ourselves (tools/zxing-cpp-build)
// instead of consuming the prebuilt io.github.zxing-cpp:android AAR, so the
// Kotlin half of that AAR has to come with it.
//
// The package and class name are load-bearing: the native library exports
// Java_zxingcpp_BarcodeReader_readYBuffer / _readBitmap, so moving or renaming
// this class breaks the JNI lookup at runtime with no build error. R8 would do
// the same, which is why amethyst/proguard-rules.pro keeps zxingcpp.** -- that
// rule used to arrive as the AAR's consumer rule and is ours to carry now.
//
// It keeps its upstream Apache-2.0 header and is excluded from spotless in the
// root build.gradle.kts: stamping our MIT header on someone else's file would
// misstate its provenance. To update, re-run build-zxingcpp.sh at a new tag and
// re-copy this file from the pinned clone so the two halves cannot drift.
// -----------------------------------------------------------------------------

package zxingcpp

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

public class BarcodeReader(public var options: Options = Options()) {
	private val supportedYUVFormats: List<Int> =
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			listOf(ImageFormat.YUV_420_888, ImageFormat.YUV_422_888, ImageFormat.YUV_444_888)
		} else {
			listOf(ImageFormat.YUV_420_888)
		}

	init {
		System.loadLibrary("zxingcpp_android")
	}

	// Enumerates barcode formats known to this package.
	// Note that this has to be kept synchronized with native (C++/JNI) side.
	public enum class Format(public val value: Int) {
		NONE                (0x0000),
		ALL                 (0x2A2A),
		ALL_READABLE        (0x722A),
		ALL_CREATABLE       (0x772A),
		ALL_LINEAR          (0x6C2A),
		ALL_MATRIX          (0x6D2A),
		ALL_GS1             (0x472A),
		ALL_RETAIL          (0x522A),
		ALL_INDUSTRIAL      (0x492A),
		CODABAR             (0x2046),
		CODE_39             (0x2041),
		CODE_39_STD         (0x7341),
		CODE_39_EXT         (0x6541),
		CODE_32             (0x3241),
		PZN                 (0x7041),
		CODE_93             (0x2047),
		CODE_128            (0x2043),
		ITF                 (0x2049),
		ITF_14              (0x3449),
		DATA_BAR            (0x2065),
		DATA_BAR_OMNI       (0x6F65),
		DATA_BAR_STK        (0x7365),
		DATA_BAR_STK_OMNI   (0x4F65),
		DATA_BAR_LTD        (0x6C65),
		DATA_BAR_EXP        (0x6565),
		DATA_BAR_EXP_STK    (0x4565),
		EAN_UPC             (0x2045),
		EAN_13              (0x3145),
		EAN_8               (0x3845),
		EAN_5               (0x3545),
		EAN_2               (0x3245),
		ISBN                (0x6945),
		UPC_A               (0x6145),
		UPC_E               (0x6545),
		TELEPEN             (0x2042),
		TELEPEN_ALPHA       (0x3042),
		TELEPEN_NUMERIC     (0x3142),
		OTHER_BARCODE       (0x2058),
		DX_FILM_EDGE        (0x7858),
		PDF_417             (0x204C),
		COMPACT_PDF_417     (0x634C),
		MICRO_PDF_417       (0x6D4C),
		AZTEC               (0x207A),
		AZTEC_CODE          (0x637A),
		AZTEC_RUNE          (0x727A),
		QR_CODE             (0x2051),
		QR_CODE_MODEL_1     (0x3151),
		QR_CODE_MODEL_2     (0x3251),
		MICRO_QR_CODE       (0x6D51),
		RMQR_CODE           (0x7251),
		DATA_MATRIX         (0x2064),
		MAXI_CODE           (0x2055),
	}

	public enum class ContentType {
		TEXT, BINARY, MIXED, GS1, ISO15434, UNKNOWN_ECI
	}

	public enum class Binarizer {
		LOCAL_AVERAGE, GLOBAL_HISTOGRAM, FIXED_THRESHOLD, BOOL_CAST
	}

	public enum class EanAddOnSymbol {
		IGNORE, READ, REQUIRE
	}

	public enum class TextMode {
		PLAIN, ECI, HRI, ESCAPED, HEX, HEX_ECI
	}

	public enum class ErrorType {
		NONE, FORMAT, CHECKSUM, UNSUPPORTED
	}

	public data class Options(
		var formats: Set<Format> = setOf(),
		var tryHarder: Boolean = false,
		var tryRotate: Boolean = false,
		var tryInvert: Boolean = false,
		var tryDownscale: Boolean = false,
		var tryDenoise: Boolean = false,
		var isPure: Boolean = false,
		var binarizer: Binarizer = Binarizer.LOCAL_AVERAGE,
		var downscaleFactor: Int = 3,
		var downscaleThreshold: Int = 500,
		var minLineCount: Int = 2,
		var maxNumberOfSymbols: Int = 0xff,
		var validateOptionalChecksum: Boolean = false,
		@Deprecated("See https://github.com/zxing-cpp/zxing-cpp/discussions/704")
		var tryCode39ExtendedMode: Boolean = true,
		@Deprecated("Use validateOptionalChecksum")
		var validateCode39CheckSum: Boolean = false,
		@Deprecated("Use validateOptionalChecksum")
		var validateITFCheckSum: Boolean = false,
		var returnErrors: Boolean = false,
		var eanAddOnSymbol: EanAddOnSymbol = EanAddOnSymbol.IGNORE,
		var textMode: TextMode = TextMode.HRI,
	)

	public data class Error(
		val type: ErrorType,
		val message: String
	)

	public data class Position(
		val topLeft: Point,
		val topRight: Point,
		val bottomRight: Point,
		val bottomLeft: Point,
		val orientation: Double
	)

	public data class Result(
		val format: Format,
		val bytes: ByteArray?,
		val text: String?,
		val contentType: ContentType,
		val position: Position,
		val orientation: Int,
		val ecLevel: String?,
		val symbologyIdentifier: String?,
		val sequenceSize: Int,
		val sequenceIndex: Int,
		val sequenceId: String?,
		val readerInit: Boolean,
		val lineCount: Int,
		val error: Error?,
	)

	public val lastReadTime : Int = 0 // runtime of last read call in ms (for debugging purposes only)

	public fun read(image: ImageProxy): List<Result> {
		check(image.format in supportedYUVFormats) {
			"Invalid image format: ${image.format}. Must be one of: $supportedYUVFormats"
		}

		return readYBuffer(
			image.planes[0].buffer,
			image.planes[0].rowStride,
			image.cropRect.left,
			image.cropRect.top,
			image.cropRect.width(),
			image.cropRect.height(),
			image.imageInfo.rotationDegrees,
			options
		)
	}

	public fun read(
		bitmap: Bitmap, cropRect: Rect = Rect(), rotation: Int = 0
	): List<Result> {
		return readBitmap(
			bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height(), rotation, options
		)
	}

	private external fun readYBuffer(
		yBuffer: ByteBuffer, rowStride: Int, left: Int, top: Int, width: Int, height: Int, rotation: Int, options: Options
	): List<Result>

	private external fun readBitmap(
		bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int, rotation: Int, options: Options
	): List<Result>
}
