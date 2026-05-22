# Keep TorrentManager native (JNI) methods so R8 doesn't rename them
-keep class com.jpcexample.simpletorrent.data.TorrentManager {
    native <methods>;
}

# Keep @Serializable classes and their companion serializers (kotlinx.serialization)
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
}
