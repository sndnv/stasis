-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn org.slf4j.impl.StaticLoggerBinder

# used for (error) messages and notifications
-keepnames class stasis.client_android.lib.ops.Operation$* { *; }

# used for analytics, stack traces and debugging
-keepnames class stasis.client_android.lib.telemetry.analytics.AnalyticsEntry$* { *; }
-keepnames class * extends java.lang.Throwable
-keepattributes SourceFile,LineNumberTable
