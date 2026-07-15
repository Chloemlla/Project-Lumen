# Keep CrashAuthorAttribution public field names for multi-point integrity checks.
-keep class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
}
-keep class com.chloemlla.lumen.crash.AuthorIntegrity {
    public static *** verifyOrThrow();
    public static *** fingerprintHex();
}
