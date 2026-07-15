# Lumen Crash SDK consumer ProGuard / R8 rules
# Merged into host apps that depend on this AAR / project module.
#
# Required minify exemption:
# keep author attribution, integrity checks, and public crash API so
# host release builds do not white-screen / fail-closed on startup.

-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, RuntimeVisibleAnnotations

# Required: author attribution constants must keep source values/names.
-keep class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
    public static *** payload();
}
-keepclassmembers class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
}

# Required: integrity entry points used on install / report / UI open.
-keep class com.chloemlla.lumen.crash.AuthorIntegrity {
    public static *** verifyOrThrow(...);
    public static *** fingerprintHex();
    public static *** verifiedAuthorBlock();
}
-keep class com.chloemlla.lumen.crash.AuthorBlock { *; }

# Required: public host integration API.
-keep class com.chloemlla.lumen.crash.LumenCrash { *; }
-keep class com.chloemlla.lumen.crash.LumenCrashConfig { *; }
-keep class com.chloemlla.lumen.crash.CrashReport { *; }
-keep class com.chloemlla.lumen.crash.CrashAppInfo { *; }
-keep class com.chloemlla.lumen.crash.CrashReportStore { *; }
-keep class com.chloemlla.lumen.crash.CrashBreadcrumbs { *; }
-keep class com.chloemlla.lumen.crash.CrashReportPasteUploader { *; }
-keep class com.chloemlla.lumen.crash.ui.LumenCrashReportScreenKt { *; }

# Package-level exemption (safe default for release minify hosts).
-keep class com.chloemlla.lumen.crash.** { *; }
-keepclassmembers class com.chloemlla.lumen.crash.** { *; }
-dontwarn com.chloemlla.lumen.crash.**
