-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn org.slf4j.impl.StaticLoggerBinder

# ical4j / ical4j-vcard: TZ cache impl and content factories are loaded reflectively
# (Class.forName + ServiceLoader); keep them and ignore the optional deps we don't ship.
-keep class net.fortuna.ical4j.** { *; }
-keep class org.threeten.extra.** { *; }
-dontwarn net.fortuna.ical4j.**
-dontwarn javax.cache.**
-dontwarn com.github.benmanes.caffeine.**
-dontwarn org.jparsec.**
-dontwarn groovy.**
-dontwarn org.codehaus.groovy.**
-dontwarn org.ccil.cowan.tagsoup.**

# used for (error) messages and notifications
-keepnames class stasis.client_android.lib.ops.Operation$* { *; }

# used for analytics, stack traces and debugging
-keepnames class stasis.client_android.lib.telemetry.analytics.AnalyticsEntry$* { *; }
-keepnames class * extends java.lang.Throwable
-keepattributes SourceFile,LineNumberTable
