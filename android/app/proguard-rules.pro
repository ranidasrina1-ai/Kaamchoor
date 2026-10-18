# WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Socket.IO
-dontwarn io.socket.**
-keep class io.socket.** { *; }
-keep class org.json.** { *; }

# Retrofit / Gson models
-keep class com.kaamchor.data.remote.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
