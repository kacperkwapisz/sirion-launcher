@file:Suppress("PackageName")

package Interface

/**
 * JNI binding for the unit's tuner library, `/system/lib/libRadioInterface.so`
 * (NXP TEF6606 over `/dev/tef6606_i2c`). The package and class name must stay
 * exactly `Interface.RadioInterface`, because the library's native functions are
 * named after it. Only the root helper loads this; the launcher never does.
 */
class RadioInterface(private val onEvent: (String) -> Unit) {
    external fun openDevice(): Int
    external fun closeDevice(): Int
    external fun setFreq(kilohertz: Int)
    external fun seekUp(kilohertz: Int)
    external fun seekDown(kilohertz: Int)
    external fun seekStop()
    external fun setAudioToRadio()
    external fun setAudioToGps()
    external fun setBand(band: Int)
    external fun setArea(area: Int)
    external fun setRDSState(on: Int)
    external fun getSignal()
    private external fun setCallBack(listener: Any?)
    private external fun resetCallBack(listener: Any?)

    fun listen() = setCallBack(this)

    fun stopListening() = resetCallBack(this)

    /** Called by the library with a JSON status string (frequency, stereo, RDS). */
    @Suppress("unused")
    fun callBack(json: String) = onEvent(json)

    companion object {
        fun load() = System.load("/system/lib/libRadioInterface.so")
    }
}
