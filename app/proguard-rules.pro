# libxposed API 102 & stability-first R8 rules
-dontobfuscate
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-dontwarn io.github.libxposed.**
-dontwarn org.luckypray.dexkit.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep class com.scluzlep.miimeprivacy.** { *; }
-keep class org.luckypray.dexkit.** { *; }
