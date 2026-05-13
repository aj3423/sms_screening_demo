package sms.screening.provider

object ScreeningRules {
    const val timeoutSimulationNumber = "6000"
    const val timeoutSimulationDelayMillis = 6_000L

    data class Decision(
        val blocked: Boolean,
        val blockReason: String?,
        val confidence: Int,
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
                confidence = 95,
            )
        }
        if (simSlot == 2) {
            return Decision(
                blocked = true,
                blockReason = "SIM slot 2",
                confidence = 90,
            )
        }
        if (smsContent.orEmpty().contains("spam", ignoreCase = true)) {
            return Decision(
                blocked = true,
                blockReason = "Content contains spam",
                confidence = 85,
            )
        }
        return Decision(
            blocked = false,
            blockReason = null,
            confidence = 0,
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
