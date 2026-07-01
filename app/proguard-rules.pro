-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# Android framework and WorkManager instantiate these classes by name and call their lifecycle methods.
-keep,allowoptimization class com.projectlumen.app.MainActivity {
    public <init>();
    public <methods>;
    protected <methods>;
}
-keep,allowoptimization class com.projectlumen.app.ProjectLumenApplication {
    public <init>();
    public <methods>;
    protected <methods>;
}
-keep,allowoptimization class com.projectlumen.app.** extends android.content.BroadcastReceiver {
    public <init>();
    public void onReceive(android.content.Context, android.content.Intent);
}
-keep,allowoptimization class com.projectlumen.app.** extends android.app.Service {
    public <init>();
    public <methods>;
    protected <methods>;
}
-keep,allowoptimization class com.projectlumen.app.** extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
    public <methods>;
    protected <methods>;
}

# Room needs stable database entry points; app entities/DAOs are small and persisted.
-keep,allowoptimization class com.projectlumen.app.core.database.AppDatabase { *; }
-keep,allowoptimization class com.projectlumen.app.core.database.AppDatabase_Impl { *; }
-keep,allowoptimization @androidx.room.Entity class com.projectlumen.app.core.database.entities.** { *; }
-keep,allowoptimization @androidx.room.Dao interface com.projectlumen.app.core.database.daos.** { *; }

# Persisted enum names are stored in Room/preferences and compared as strings.
-keepclassmembers enum com.projectlumen.app.core.enums.** {
    public static final <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# WebView JavaScript bridge methods are invoked by their source names.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.projectlumen.app.core.security.NativeSecurityBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**
-dontwarn androidx.navigation.**
-dontwarn androidx.room.**
