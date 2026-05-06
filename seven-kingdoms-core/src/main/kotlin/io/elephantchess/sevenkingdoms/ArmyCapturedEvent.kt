package io.elephantchess.sevenkingdoms

data class ArmyCapturedEvent(
    val capturingColor: Color,
    val capturedColor: Color,
    val generalCapture: Boolean
) {

    override fun toString(): String {
        val middle =
            if (generalCapture) {
                "$capturingColor captures $capturedColor general"
            } else {
                "$capturingColor captures $capturedColor army"
            }

        return "${javaClass.simpleName}{$middle}"
    }

}
