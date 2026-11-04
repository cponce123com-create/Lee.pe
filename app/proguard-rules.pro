# R8 / ProGuard rules for Lee.pe (release build).
#
# Readium, Media3 and Room provide consumer rules for their generated/runtime
# types. Keep only Room's generated database entry points; avoid retaining whole
# third-party packages so release shrinking remains effective.

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Readium (EPUB engine) -------------------------------------------------
-dontwarn org.readium.**

# --- Media3 (audio + TTS sessions) ----------------------------------------
-dontwarn androidx.media3.**

# --- Room 3 ---------------------------------------------------------------
-keep class * extends androidx.room3.RoomDatabase { *; }
-keep @androidx.room3.Entity class * { *; }
-keep @androidx.room3.Dao interface * { *; }
-dontwarn androidx.room3.**

# --- Kotlin / coroutines ---------------------------------------------------
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**

