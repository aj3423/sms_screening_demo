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

class PublicService : Service() {
    companion object {
        private const val logTag = "PublicSmsScreening"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val messenger = Messenger(
        Handler(mainHandler.looper) { message ->
            when (message.what) {
                Protocol.messageQueryShouldBlock -> {
                    handleQuery(message)
                    true
                }

                else -> false
            }
        }
    )

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun handleQuery(message: Message) {
        val requestData = message.data ?: Bundle.EMPTY
        val number = requestData.getString(Protocol.keyNumber)
        val smsContent = requestData.getString(Protocol.keySmsContent)
        val simSlot = requestData.takeIf {
            it.containsKey(Protocol.keySimSlot)
        }?.getInt(Protocol.keySimSlot)
        val decision = ScreeningRules.shouldBlock(
            number = number,
            smsContent = smsContent,
            simSlot = simSlot,
        )
        val replyMessenger = message.replyTo ?: run {
            Log.w(logTag, "Ignoring screening query without reply messenger.")
            return
        }

        val sendResult = Runnable {
            val response = Message.obtain(
                null,
                Protocol.messageScreeningResult,
            ).apply {
                data = Bundle().apply {
                    putBoolean(Protocol.keyShouldBlock, decision.blocked)
                    putString(Protocol.keyReason, decision.blockReason)
                }
            }

            try {
                replyMessenger.send(response)
            } catch (_: RemoteException) {
                Log.w(logTag, "Failed to deliver screening result to caller.")
            }
        }

        if (ScreeningRules.shouldSimulateTimeout(number)) {
            Log.i(
                logTag,
                "Delaying response for ${ScreeningRules.timeoutSimulationDelayMillis} ms to simulate timeout."
            )
            mainHandler.postDelayed(sendResult, ScreeningRules.timeoutSimulationDelayMillis)
        } else {
            sendResult.run()
        }
    }
}
