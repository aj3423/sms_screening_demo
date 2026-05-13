package sms.screening.provider

object ScreeningRules {
    const val timeoutSimulationNumber = "6000"
    const val timeoutSimulationDelayMillis = 6_000L

    fun shouldBlock(
        number: String?,
        smsContent: String?,
        simSlot: Int?,
    ): Boolean {
        if (isEvenNumber(number)) {
            return true
        }
        if (simSlot == 2) {
            return true
        }
        if (smsContent.orEmpty().contains("spam", ignoreCase = true)) {
            return true
        }
        return false
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
