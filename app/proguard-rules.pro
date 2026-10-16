# ============================================================================
# Release build keep rules for com.pupil.hanzi.
#
# R8 shrinking in AGP 9.4 removes kotlin.sequences.SequenceScope (and the
# inlined sequence-builder anonymous class) because it sees no direct call,
# but Compose / coroutines code paths still extend it at runtime. Launching
# then dies with:
#
#   java.lang.IllegalAccessError: Class kotlin.sequences.X extended by class Y
#   is inaccessible
#
# at AbstractComposeView.onAttachedToWindow -> DefaultScheduler.<clinit>.
# Keep the whole kotlin.sequences package so R8 cannot drop any of it.
# ============================================================================

# --- Kotlin sequences: SequenceScope / SequenceBuilderIterator used by the
#     sequence{} DSL, referenced from coroutines and Compose internals.
-keep class kotlin.sequences.** { *; }
-dontnote kotlin.sequences.**

# --- HanLP: segments text and loads dictionaries by classpath resource and
#     reflective access.
-keep class com.hankcs.** { *; }
-dontwarn com.hankcs.**

# --- kotlinx-serialization: generated serializers must keep their names.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class **$$serializer { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
