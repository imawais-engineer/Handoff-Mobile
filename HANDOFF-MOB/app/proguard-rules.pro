# ProGuard / R8 rules for Handoff Files
-keep class com.handoff.files.protocol.** { *; }
-keep class com.handoff.files.domain.model.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
