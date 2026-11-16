# =============================================================================
# R8 configuration for the release build.
#
# Two things are balanced here.
#
#  1. Google Play measures how much of the shipped DEX R8 actually optimized
#     and renamed, and warns (then restricts visibility/publishing) below 25%
#     in either category. This file used to open with `-dontobfuscate` plus
#     `-keepnames class ** { *; }`, and then kept all of `com.vitorpamplona.**`
#     outright. That is the whole app and every library: `-dontobfuscate`
#     turns renaming off globally, and `-keepnames` is shorthand for
#     `-keep,allowshrinking`, which permits shrinking but neither renaming nor
#     optimization. Hence 0% obfuscation / 13% optimization.
#
#  2. Anything the runtime reaches by NAME rather than by reference has to keep
#     that name: JNI symbols, Jackson's reflective data binding, enum constants
#     persisted into DataStore, class names written into a manifest meta-data
#     value or into WorkManager's database.
#
# So every keep below is scoped to (2), and R8 gets everything else. mapping.txt
# is uploaded with each release, so stack traces stay retraceable.
#
# When adding a keep, say in a comment WHAT reads the name at runtime. A keep
# without that is usually a keep that is not needed.
# =============================================================================

# -----------------------------------------------------------------------------
# Attributes
# -----------------------------------------------------------------------------
# These two are what make a crash report readable again. Keep them.
#
# There is no setting that gives readable stack traces *in the raw trace* once
# R8 is minifying, and that is worth being precise about, because it is the
# thing -dontobfuscate used to buy us:
#
#   * R8 overwrites every class's SourceFile with the marker
#     `r8-map-id-<hash>` whatever we do here. Verified by building it both
#     ways: with `-renamesourcefileattribute SourceFile` all 24,440 classes
#     report the literal "SourceFile"; without it they report the marker.
#     There is no rule that restores the original per-class .kt name.
#   * R8 renumbers lines even with LineNumberTable kept, because one
#     obfuscated line now has to encode a whole INLINED frame stack. In this
#     build, line 7 of one method carries three source frames:
#     TextFieldCharSequence.getText():58 inlined into TextFieldState.getText()
#     :146 inlined into ShortNotePostViewModel.onMessageChanged():1727. A raw
#     line number is no longer a source line.
#
# That second point is a cost of OPTIMIZATION, not of renaming, and it is new
# here only because the old `-keepnames class ** { *; }` had optimization off
# program-wide — which is exactly what Play was complaining about.
#
# So the answer is retrace, not a keep rule. And retrace hands back more than
# the old raw traces did: it expands those inlined frames instead of collapsing
# them into one misleading line. See `scripts/retrace.sh` and RELEASE_OPS.md
# § 7. Two things make that painless, and both depend on this file:
#
#   * `-renamesourcefileattribute` is deliberately NOT set, so the map-id
#     marker survives. A pasted trace then names the exact mapping file it
#     needs (the marker is the `pg_map_id` header of that mapping), so there is
#     never any doubt about which release a report came from.
#   * mapping.txt.gz ships as a GitHub Release asset for every build, so traces
#     from F-Droid / Zapstore / Accrescent users are retraceable too — Play
#     Console only auto-deobfuscates the AAB it was given.
-keepattributes SourceFile,LineNumberTable

# Generic signatures, plus the inner/enclosing-class links that travel with them.
#
# Signature carries the generic type arguments R8 would otherwise erase.
#
# It is NOT enough to make `object : TypeReference<T>() {}` work under full mode
# (android.enableR8.fullMode=true). Measured on device: JacksonMapper's <clinit>
# built its JavaTypes through jacksonTypeRefOf() and threw
#
#   IllegalArgumentException: Internal error: TypeReference constructed without
#   actual type information
#
# which poisons the class -- every later use is NoClassDefFoundError, so nothing
# could be signed or sent. Keeping the anonymous subclasses did not help either
# (tried -keep,allowobfuscation and a full -keep ... { *; }). The fix was to stop
# asking Jackson to read the type back off the class: JacksonMapper now builds
# those JavaTypes with TypeFactory.constructType/constructCollectionType/
# constructParametricType, which take the Class objects directly.
#
# Anything else that still resolves a generic type reflectively is exposed the
# same way -- notably JacksonMapper.fromJsonTo<T>, JsonMapperNip55 (NIP-55) and
# the NIP-46 bunker path, which were not reachable in this test run.
#
# All three are ALSO in AGP's proguard-android-optimize.txt, which keeps
# AnnotationDefault, EnclosingMethod, InnerClasses, Signature and the three
# RuntimeVisible* annotation attributes. They stay spelled out here anyway: the
# duplicate is free (measured at 0 bytes) and it means a change to AGP's default
# file cannot quietly take Signature away from Jackson.
#
# Two attributes this line used to carry are gone, both measured on the arm64
# release DEX:
#
#   * `*Annotation*` — over AGP's default its only contribution was the
#     RuntimeInvisible* variants, which by definition cannot be read at runtime.
#     Dropping it produced a byte-identical DEX (31,390,048 either way). Nothing
#     we ship reads an annotation reflectively, and the libraries that do
#     (kotlinx.serialization, appfunctions, AppSearch) match on RuntimeVisible*,
#     which AGP already keeps.
#   * `Exceptions` — @Throws metadata for 31 methods in shipped code, read by
#     Java-interop compilers and by nothing at runtime. Worth 692 bytes.
#
# For the record, since it was wrong here for a while: this line used to credit
# jackson-module-kotlin with reading @kotlin.Metadata through it. That module no
# longer ships, and the Jackson mixins it also named were deleted along with the
# NWC Jackson path.
-keepattributes Signature,InnerClasses,EnclosingMethod

# LocalVariableTable, LocalVariableTypeTable, MethodParameters and
# -keepparameternames used to be kept here as well. They are debug metadata:
# nothing in the app reads them (jackson-module-kotlin takes parameter names
# from @kotlin.Metadata, not from MethodParameters), and they are pure DEX
# weight in a release build.

-keepdirectories libs

# -----------------------------------------------------------------------------
# JNI — names that live in a .so, not in the DEX
# -----------------------------------------------------------------------------
# proguard-android-optimize.txt already contributes
#   -keepclasseswithmembernames class * { native <methods>; }
# which pins every class that DECLARES a native method (ArtiNative,
# secp256k1's loader, …) together with those methods' names, because the
# exported symbol is Java_<class>_<method>. What that does NOT cover is the
# traffic in the other direction: Java/Kotlin the native side looks up itself.

# libarti_android.so calls back into this interface by name —
# tools/arti-build/src/lib.rs does GetMethodID("onLogLine") on it. Renaming the
# method silently kills all Tor log output.
-keep class com.vitorpamplona.amethyst.ui.tor.ArtiLogCallback { *; }

# secp256k1's JNI layer resolves these from native code.
-keep class fr.acinq.secp256k1.** { *; }

# zxing-cpp's JNI boundary. The native library exports name-mangled symbols
# (Java_zxingcpp_BarcodeReader_readYBuffer), so R8 renaming the class or its
# external methods breaks the lookup at runtime with no build error and no
# warning -- the QR scanner simply fails to start in release builds.
#
# This arrived automatically as the io.github.zxing-cpp:android AAR's consumer
# rule. We build that library ourselves now (tools/zxing-cpp-build) and vendor
# its Kotlin half, so the rule is ours to carry.
-keep class zxingcpp.** { *; }

# Nothing keeps libscrypt, NetCipher/tor-android, LazySodium or JNA any more:
# quartz replaced libsodium with a pure-Kotlin implementation (LibSodiumInstance)
# and Tor now runs through arti's own JNI layer. Their rules used to live here and
# matched zero classes in the release build — none of those artifacts appear in
# mapping.txt, usage.txt or seeds.txt, i.e. they are not on the classpath at all.
# If one ever comes back, so must its rule: JNA in particular maps types onto the
# C ABI by reflecting over their fields and method signatures at runtime.

# -----------------------------------------------------------------------------
# Enum constant names
# -----------------------------------------------------------------------------
# An enum constant's NAME is persisted data in this app. The preference stores
# write `enum.name` into DataStore and read it back with `Type.valueOf(string)`
# (see model/preferences/UISharedPreferences.kt, TorSharedPreferences.kt,
# NamecoinSharedPreferences.kt), and Jackson serialises enums by name too.
# Enum.valueOf resolves that string against the static FIELD name, so renaming
# the constants would reset every user's theme/font/Tor/connectivity setting on
# the first launch after an update.
#
# Deliberately blanket rather than a list of the enums that happen to be
# persisted today: the failure mode is silent, release-only, and one new
# `preferences[KEY] = value.name` line away. Only the field names are pinned —
# the enum classes themselves are still renamed and their methods still
# optimized.
-keepclassmembers enum * {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -----------------------------------------------------------------------------
# JSON — nothing to keep
# -----------------------------------------------------------------------------
# Quartz 2.4.0 dropped Jackson. Nostr JSON (events, filters, messages, commands,
# rumors, templates, tags, NIP-46/NIP-55) goes through quartz's hand-written Kite
# reader/writer, and everything else (NIP-47, CLINK, the on-disk stores) through
# @Serializable kotlinx classes. Both name every field as a string literal, so
# R8 can rename the Kotlin fields freely without changing the wire format. The
# on-disk formats are pinned by ScheduledPostFileFormatTest and
# PowAndUsageFileFormatTest.

# -----------------------------------------------------------------------------
# Names referenced from outside the DEX
# -----------------------------------------------------------------------------
# AGP generates keeps from the merged manifest's component `android:name`
# attributes, but NOT from <meta-data android:value>. The Cast framework reads
# this one out of the manifest and Class.forName()s it.
-keep class com.vitorpamplona.amethyst.service.cast.chromecast.AmethystCastOptionsProvider { *; }

# NOT a rule for WorkManager. It stores the worker's class name in its own
# database at enqueue time and instantiates it by name on a later process start
# — including after an app update that reshuffled the mapping — so the name does
# have to survive. But androidx.work already ships exactly that in its own
# consumer rules (`-keepnames class * extends androidx.work.ListenableWorker`
# plus a keepclassmembers for the public constructors), so a rule here was pure
# duplication. The three workers stay listed in the reflection contract, which
# now verifies the LIBRARY's rule keeps doing the job.

# androidx.appfunctions: the KSP-generated invokers and the app_functions.xml
# the system reads are keyed off these declarations. One class plus its
# generated neighbours — cheap enough not to be worth proving unnecessary
# against a pre-stable (alpha) library.
-keep class com.vitorpamplona.amethyst.appfunctions.** { *; }

