package pl.perier.sirion.helper

/**
 * Talks to the unit's TDA7729 audio chip through the Autochips HIDL service
 * `vendor.autochips.hardware.audio@1.0::IAudioEffect`, the same call the
 * vendor sound app makes (`effect_setValue(id, value)`, transaction 1).
 *
 * HwBinder and HwParcel are hidden on 8.1, so they're reached by reflection.
 */
object AudioChip {
    private const val INTERFACE = "vendor.autochips.hardware.audio@1.0::IAudioEffect"
    private const val SET_VALUE = 1

    private val parcelClass by lazy { Class.forName("android.os.HwParcel") }
    private val service by lazy {
        Class.forName("android.os.HwBinder")
            .getMethod("getService", String::class.java, String::class.java)
            .invoke(null, INTERFACE, "default")
            ?: error("audio effect service not found")
    }

    fun set(id: Int, value: Int): Int {
        val request = parcelClass.getConstructor().newInstance()
        val reply = parcelClass.getConstructor().newInstance()
        parcelClass.getMethod("writeInterfaceToken", String::class.java).invoke(request, INTERFACE)
        val writeInt = parcelClass.getMethod("writeInt32", Int::class.javaPrimitiveType)
        writeInt.invoke(request, id)
        writeInt.invoke(request, value)
        try {
            Class.forName("android.os.IHwBinder")
                .getMethod("transact", Int::class.javaPrimitiveType, parcelClass, parcelClass, Int::class.javaPrimitiveType)
                .invoke(service, SET_VALUE, request, reply, 0)
            parcelClass.getMethod("verifySuccess").invoke(reply)
            parcelClass.getMethod("releaseTemporaryStorage").invoke(request)
            return parcelClass.getMethod("readInt32").invoke(reply) as Int
        } finally {
            parcelClass.getMethod("release").invoke(reply)
        }
    }
}
