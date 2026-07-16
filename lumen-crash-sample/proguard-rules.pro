# Sample host proguard. Prefer AAR consumer-rules; keep explicit package backup.
-keep class com.chloemlla.lumen.crash.** { *; }
-keepclassmembers class com.chloemlla.lumen.crash.** { *; }
-dontwarn com.chloemlla.lumen.crash.**
