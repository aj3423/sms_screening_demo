package sms.screening.provider

object ScreeningRules {
    const val timeoutSimulationNumber = "6000"
    const val timeoutSimulationDelayMillis = 6_000L

    data class Decision(
        val blocked: Boolean,
        val blockReason: String?,
    )

    fun shouldBlock(
        number: String?,
        smsContent: String?,
        simSlot: Int?,
    ): Decision {
        if (isEvenNumber(number)) {
            return Decision(
                blocked = true,
                blockReason = "Even number",
            )
        }
        if (simSlot == 2) {
            return Decision(
                blocked = true,
                blockReason = "SIM slot 2",
            )
        }
        if (smsContent.orEmpty().contains("spam", ignoreCase = true)) {
            return Decision(
                blocked = true,
                blockReason = "Content contains `spam`",
            )
        }
        return Decision(
            blocked = false,
            blockReason = null,
        )
    }

    fun shouldSimulateTimeout(number: String?): Boolean {
        return number.orEmpty().filter(Char::isDigit) == timeoutSimulationNumber
    }

    private fun isEvenNumber(number: String?): Boolean {
        val normalized = number.orEmpty().filter(Char::isDigit)
        if (normalized.isEmpty()) {
            return false
        }
        return normalized.last().digitToInt() % 2 == 0
    }
}
