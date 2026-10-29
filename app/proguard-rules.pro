# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class io.github.smyrna_pergamum.exercisetracker.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.smyrna_pergamum.exercisetracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
