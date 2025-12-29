-keep class com.google.crypto.tink.** { *; }
-keep class com.google.crypto.tink.shaded.protobuf.** { *; }

-keep class com.microsoft.signalr.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**
-dontwarn com.google.crypto.tink.util.KeysDownloader