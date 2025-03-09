package io.elephantchess.xiangqi

data class PerpetualCheckingRule(val numberOfPieces: Int, val numberOfChecks: Int) {

    companion object {

        val defaultPerpetualCheckingRules: List<PerpetualCheckingRule>
            get() = listOf(
                PerpetualCheckingRule(1, 6),
                PerpetualCheckingRule(2, 12),
                PerpetualCheckingRule(3, 18),
            )
    }

}
