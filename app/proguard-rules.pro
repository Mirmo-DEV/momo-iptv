# kotlinx.serialization : garder les sérialiseurs générés pour nos classes (comptes, cache, Ma liste…)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.momo.iptv.** {
    *** Companion;
}
-keepclasseswithmembers class com.momo.iptv.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.momo.iptv.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.momo.iptv.** {
    *;
}

# OkHttp / Okio : avertissements sans conséquence
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
