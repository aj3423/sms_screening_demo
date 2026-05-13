package sms.screening.provider

import android.app.Service
import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException

class PublicSmsScreeningService : Service() {
    companion object {
        private const val logTag = "PublicSmsScreening"
    }

    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                PublicSmsScreeningProtocol.messageQueryShouldBlock -> {
                    handleQuery(message)
                    true
                }

                else -> false
            }
        }
    )

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun handleQuery(message: Message) {
        val blocked = ScreeningRules.shouldBlock(
            number = message.data?.getString(PublicSmsScreeningProtocol.keyNumber),
            smsContent = message.data?.getString(PublicSmsScreeningProtocol.keySmsContent),
            simSlot = message.data?.getInt(PublicSmsScreeningProtocol.keySimSlot, 1) ?: 1,
        )
        val replyMessenger = message.replyTo ?: run {
            Log.w(logTag, "Ignoring screening query without reply messenger.")
            return
        }
        val response = Message.obtain(
            null,
            PublicSmsScreeningProtocol.messageScreeningResult,
        ).apply {
            data = Bundle().apply {
                putBoolean(PublicSmsScreeningProtocol.keyBlocked, blocked)
            }
        }

        try {
            replyMessenger.send(response)
        } catch (_: RemoteException) {
            Log.w(logTag, "Failed to deliver screening result to caller.")
        }
    }
}
