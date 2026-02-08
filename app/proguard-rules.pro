# ── General ────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ── Gson (used by Ktor serialization) ─────────────────────────────
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Keep data classes used for serialization
-keep class me.stappmus.messagegateway.domain.** { *; }
-keep class me.stappmus.messagegateway.modules.localserver.domain.** { *; }
-keep class me.stappmus.messagegateway.modules.webhooks.domain.** { *; }
-keep class me.stappmus.messagegateway.modules.webhooks.payload.** { *; }
-keep class me.stappmus.messagegateway.modules.messages.data.** { *; }
-keep class me.stappmus.messagegateway.data.entities.** { *; }

# ── Ktor ──────────────────────────────────────────────────────────
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── Netty (used by Ktor server) ───────────────────────────────────
-keep class io.netty.** { *; }
-dontwarn io.netty.**

# ── OkHttp ────────────────────────────────────────────────────────
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Koin ──────────────────────────────────────────────────────────
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# ── Room ──────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Firebase ──────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ── Coroutines ────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**

# ── klinker SMS/MMS library ───────────────────────────────────────
-keep class com.klinker.android.** { *; }
-dontwarn com.klinker.android.**

# ── Enums (used by Room converters & Gson) ────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}