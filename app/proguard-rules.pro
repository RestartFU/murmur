# sherpa-onnx's JNI layer reads and constructs these classes by name.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# commons-compress references optional codecs we never use.
-dontwarn org.apache.commons.compress.**
-dontwarn org.brotli.dec.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**
-dontwarn org.objectweb.asm.**
