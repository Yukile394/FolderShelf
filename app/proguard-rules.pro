# FolderShelf ProGuard / R8 kurallari

# ViewBinding tarafindan uretilen siniflari koru
-keep class com.yukile.foldershelf.databinding.** { *; }

# Kotlin coroutines icin genel guvenlik kurallari
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }

# Uygulama veri modellerimiz (JSON alan adlari yansima/refleksiyon ile
# okunmasa da isimlerin karisik hale gelmesini onlemek icin korunur)
-keep class com.yukile.foldershelf.data.model.** { *; }
-keep class com.yukile.foldershelf.data.local.** { *; }

# Debug loglari icin satir numaralarini koru (kilitlenme raporlarini
# okunabilir tutmak icin)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
