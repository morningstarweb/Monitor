# Kotlin serialization and AndroidX ship their consumer R8 rules.
# Keep optimization enabled; no broad keep rules are necessary.
-dontwarn kotlin.Metadata
-dontwarn kotlin.annotation.AnnotationRetention
