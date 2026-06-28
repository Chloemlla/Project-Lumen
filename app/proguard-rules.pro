-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

-keep class com.projectlumen.app.** { *; }
-keep class androidx.room.** { *; }
-keep @androidx.room.* class * { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.app.Service
-keep class * extends android.app.Application

-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**
-dontwarn androidx.navigation.**
-dontwarn androidx.room.**
