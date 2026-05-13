package sms.screening.provider

object Protocol {

    const val messageQueryShouldBlock = 1
    const val messageScreeningResult = 2

    const val keyNumber = "number"
    const val keySmsContent = "smsContent"
    const val keySimSlot = "simSlot"
    const val keyBlocked = "blocked"
}
