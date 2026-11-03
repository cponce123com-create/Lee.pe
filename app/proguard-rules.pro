# R8 / ProGuard rules for Lee.pe (release build).
#
# Readium, Media3 and Room ship their own consumer rules, but they rely on
# reflection and on generated code in ways that break easily under R8 full mode.
# These keeps are deliberately conservative: correctness first, size second.

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Readium (EPUB engine) -------------------------------------------------
-keep class org.readium.r2.** { *; }
-keep class org.readium.navigator.** { *; }
-keep interface org.readium.** { *; }
-dontwarn org.readium.**

# --- Media3 (audio + TTS sessions) ----------------------------------------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Room 3 ---------------------------------------------------------------
-keep class androidx.room3.** { *; }
-keep class * extends androidx.room3.RoomDatabase { *; }
-keep @androidx.room3.Entity class * { *; }
-keep @androidx.room3.Dao interface * { *; }
-dontwarn androidx.room3.**

# --- Kotlin / coroutines ---------------------------------------------------
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**

# --- JSON (backup format) --------------------------------------------------
-keep class org.json.** { *; }
