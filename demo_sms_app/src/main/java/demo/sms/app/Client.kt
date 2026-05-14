package demo.sms.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val replyTimeoutMillis = 5_000L

sealed interface ScreeningQueryResult {
    data class Success(
        val blocked: Boolean,
        val blockReason: String?,
    ) : ScreeningQueryResult

    data class Failure(val message: String) : ScreeningQueryResult
}

class Client(
    private val context: Context,
) {
    fun listAvailableProviders(): List<ComponentName> {
        return context.packageManager.queryPublicScreeningProviders()
            .mapNotNull { it.serviceInfo }
            .map { ComponentName(it.packageName, it.name) }
    }

    suspend fun shouldBlock(
        number: String,
        smsContent: String?,
        simSlot: Int?,
    ): ScreeningQueryResult = withContext(Dispatchers.Main.immediate) {
        val normalizedNumber = number.trim()
        if (normalizedNumber.isEmpty()) {
            return@withContext ScreeningQueryResult.Failure("Number is required.")
        }
        val provider = listAvailableProviders().firstOrNull()
            ?: return@withContext ScreeningQueryResult.Failure(
                "No public SMS screening provider app was found."
            )

        suspendCancellableCoroutine<ScreeningQueryResult> { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            var isBound = false
            var isCompleted = false
            lateinit var connection: ServiceConnection

            fun complete(result: ScreeningQueryResult) {
                if (isCompleted) {
                    return
                }
                isCompleted = true
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            fun unbindIfNeeded() {
                if (isBound) {
                    isBound = false
                    context.unbindService(connection)
                }
            }

            val timeout = Runnable {
                unbindIfNeeded()
                complete(
                    ScreeningQueryResult.Failure("The screening provider did not reply in 5 seconds.")
                )
            }

            val replyTo = Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    mainHandler.removeCallbacks(timeout)
                    when (message.what) {
                        Protocol.messageScreeningResult -> {
                            val responseData = message.data
                            unbindIfNeeded()
                            complete(
                                ScreeningQueryResult.Success(
                                    blocked = responseData?.getBoolean(Protocol.keyShouldBlock, false) ?: false,
                                    blockReason = responseData?.getString(Protocol.keyReason),
                                )
                            )
                            true
                        }

                        else -> false
                    }
                }
            )

            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    val request = Message.obtain(
                        null,
                        Protocol.messageQueryShouldBlock,
                    ).apply {
                        this.replyTo = replyTo
                        data = Bundle().apply {
							// Number must exist
                            putString(Protocol.keyNumber, normalizedNumber)

							// smsContent and simSlot are optional
                            smsContent?.let {
                                putString(Protocol.keySmsContent, it)
                            }
                            simSlot?.let {
                                putInt(Protocol.keySimSlot, it)
                            }
                        }
                    }

                    try {
                        Messenger(service).send(request)
                        mainHandler.postDelayed(timeout, replyTimeoutMillis)
                    } catch (_: RemoteException) {
                        unbindIfNeeded()
                        complete(
                            ScreeningQueryResult.Failure("The screening provider could not receive the request.")
                        )
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    mainHandler.removeCallbacks(timeout)
                    unbindIfNeeded()
                    complete(
                        ScreeningQueryResult.Failure("The screening provider disconnected before returning a result.")
                    )
                }

                override fun onNullBinding(name: ComponentName) {
                    mainHandler.removeCallbacks(timeout)
                    unbindIfNeeded()
                    complete(
                        ScreeningQueryResult.Failure("The screening provider does not expose a usable messenger.")
                    )
                }
            }

            continuation.invokeOnCancellation {
                mainHandler.removeCallbacks(timeout)
                mainHandler.post {
                    unbindIfNeeded()
                }
            }

            val explicitIntent = Intent(Protocol.action).apply {
                component = provider
            }
            val didBind = try {
                context.bindService(explicitIntent, connection, Context.BIND_AUTO_CREATE)
            } catch (_: SecurityException) {
                complete(
                    ScreeningQueryResult.Failure("The screening provider rejected the bind request.")
                )
                false
            }

            if (didBind) {
                isBound = true
            } else if (!isCompleted) {
                complete(
                    ScreeningQueryResult.Failure("Failed to bind to the screening provider.")
                )
            }
        }
    }
}

private fun PackageManager.queryPublicScreeningProviders(): List<ResolveInfo> {
    val intent = Intent(Protocol.action)
    val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        queryIntentServices(intent, 0)
    }

    return services.filter { it.serviceInfo?.exported == true }
}
