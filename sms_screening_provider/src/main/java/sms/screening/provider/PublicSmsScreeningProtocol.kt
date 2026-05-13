package sms.screening.provider

object PublicSmsScreeningProtocol {

    const val messageQueryShouldBlock = 1
    const val messageScreeningResult = 2

    const val keyNumber = "number"
    const val keySmsContent = "smsContent"
    const val keySimSlot = "simSlot"
    const val keyBlocked = "blocked"
}
