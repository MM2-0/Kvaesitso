
# Shizuku loads this exact UserService class from the host APK.
-keep class de.mm20.launcher2.ktx.shizuku.ShizukuCommandService { public <init>(); }
-keep class de.mm20.launcher2.ktx.shizuku.ICommandService { *; }
-keep class de.mm20.launcher2.ktx.shizuku.ICommandService$Stub { *; }
