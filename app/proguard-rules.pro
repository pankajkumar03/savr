# Gson models (Retrofit responses) are read reflectively: keep their field names.
-keep class com.savr.app.data.ExtractRequest { *; }
-keep class com.savr.app.data.ExtractResponse { *; }
-keep class com.savr.app.data.QualityDto { *; }
# Retrofit suspend functions / generics
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
