# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.lrvdlinden.noodkoffer.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.lrvdlinden.noodkoffer.data.**$$serializer { *; }
