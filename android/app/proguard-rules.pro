# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.lrvdlinden.noodpakket.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.lrvdlinden.noodpakket.data.**$$serializer { *; }
