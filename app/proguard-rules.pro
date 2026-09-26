# Optimization and Obfuscation configuration for MeshGram
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# Rename and repackage all internal classes into empty root package
-repackageclasses ''
-allowaccessmodification

# Strip all android.util.Log calls for complete confidentiality
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Keep Android core components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Compose internal rules
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
    @androidx.compose.runtime.ReadOnlyComposable *;
}

# Keep Gson serialization models
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.meshgram.app.model.** { *; }
-keep class com.meshgram.app.mesh.** { *; }

# Keep WebRTC Native Interop
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Keep Nearby Connections
-keep class com.google.android.gms.nearby.** { *; }
-dontwarn com.google.android.gms.nearby.**

# Keep ZXing Core
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# General warnings suppression and attributes preservation
-dontwarn okio.**
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-ignorewarnings

