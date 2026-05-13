package sms.screening.provider

object ScreeningRules {
    fun shouldBlock(
        number: String?,
        smsContent: String?,
        simSlot: Int,
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

    private fun isEvenNumber(number: String?): Boolean {
        val normalized = number.orEmpty().filter(Char::isDigit)
        if (normalized.isEmpty()) {
            return false
        }
        return normalized.last().digitToInt() % 2 == 0
    }
}
