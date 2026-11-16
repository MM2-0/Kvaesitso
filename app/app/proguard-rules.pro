-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod,Signature
-repackageclasses ''
-allowaccessmodification

# Preserve Shizuku internal methods accessed via reflection by SmartFreezeService and TelosShellEngine
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class rikka.rish.** { *; }
-keep class rikka.hidden.** { *; }
# Hidden-compat / Shizuku-Next reference framework types that exist on the
# device, not in android.jar.
-dontwarn android.app.ContentProviderHolder
-dontwarn android.app.ActivityThread
-dontwarn android.app.ActivityThread$*
-dontwarn android.app.ContextImpl
-dontwarn android.app.IProcessObserver
-dontwarn android.app.IProcessObserver$*
-dontwarn android.app.IUidObserver
-dontwarn android.app.IUidObserver$*
-dontwarn android.app.ActivityManagerHidden
-dontwarn android.content.IContentProvider
-dontwarn android.content.pm.UserInfo
-dontwarn android.ddm.DdmHandleAppName
-dontwarn android.os.SELinux
-dontwarn android.os.ServiceManager
-dontwarn android.os.SystemProperties
-dontwarn rikka.hidden.**

# Preserve Compose Material Icons nested objects. In keep patterns `$` is the
# end-of-name token, so a literal inner-class `$` is written as `$$`. Empty
# marker objects such as Icons$AutoMirrored$Rounded are otherwise stripped
# after inlining, and Compose looks them up by Class.forName during dispatchDraw.
-keep class androidx.compose.material.icons.** { *; }
-keep class androidx.compose.material.icons.Icons { *; }
-keep class androidx.compose.material.icons.Icons$$** { *; }
-dontwarn androidx.compose.material.icons.**

-keep public class de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM {
    public <init>();
}

# Debug, verbose, and informational log calls stripped completely from release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Telos Files: SSH, SMB and FTP clients use reflection and optional libraries that Android does not have
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
# BouncyCastleProvider loads its algorithm classes by name; everything else is reached from code
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.pqc.jcajce.provider.** { *; }
-dontwarn net.schmizz.**
-dontwarn com.hierynomus.**
-dontwarn org.bouncycastle.**
-dontwarn net.i2p.crypto.**
-dontwarn org.slf4j.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.el.**
-dontwarn javax.security.auth.**
-dontwarn java.lang.management.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn net.lingala.zip4j.**

# Telos PDF tools: PdfBox-Android loads fonts, CMaps and filters by name through reflection
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter
-dontwarn org.apache.pdfbox.jbig2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**

# TvPlayerController compares this activity class name with SettingsDeepLinkContract.
-keepnames class de.mm20.launcher2.ui.settings.SettingsActivity

# JavaMail resolves these enabled transports through META-INF/javamail.providers.
-keep class com.sun.mail.imap.IMAPSSLStore { public <init>(javax.mail.Session, javax.mail.URLName); }
-keep class com.sun.mail.smtp.SMTPTransport { public <init>(javax.mail.Session, javax.mail.URLName); }
-keep class com.sun.mail.smtp.SMTPSSLTransport { public <init>(javax.mail.Session, javax.mail.URLName); }
# Activation loads these MIME content handlers from the library mailcap resource.
-keep class com.sun.mail.handlers.** implements javax.activation.DataContentHandler { public <init>(); public *; }

# Preserve the package used by AnyAscii Unicode resource lookups.
-keepnames class com.anyascii.AnyAscii

# R8 optimization passes & Kotlin runtime null-check stripping
-optimizationpasses 5
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
    public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}
