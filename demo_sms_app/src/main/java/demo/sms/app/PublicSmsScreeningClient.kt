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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

sealed interface ScreeningQueryResult {
    data class Success(
        val blocked: Boolean,
        val providerLabel: String,
    ) : ScreeningQueryResult

    data class Failure(val message: String) : ScreeningQueryResult
}

class PublicSmsScreeningClient(
    private val context: Context,
) {
    suspend fun shouldBlock(
        number: String,
        smsContent: String,
        simSlot: Int,
    ): ScreeningQueryResult = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val resolveInfo = context.packageManager.findPublicScreeningProvider()
            if (resolveInfo == null) {
                continuation.resume(
                    ScreeningQueryResult.Failure("No public SMS screening provider app was found.")
                )
                return@suspendCancellableCoroutine
            }

            val serviceInfo = resolveInfo.serviceInfo
            val providerLabel = resolveInfo.loadLabel(context.packageManager)
                .toString()
                .takeIf { it.isNotBlank() }
                ?: serviceInfo.packageName
            val mainHandler = Handler(Looper.getMainLooper())
            val bound = AtomicBoolean(false)
            val completed = AtomicBoolean(false)
            lateinit var connection: ServiceConnection

            fun complete(result: ScreeningQueryResult) {
                if (completed.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(result)
                }
            }

            fun unbindIfNeeded() {
                if (bound.compareAndSet(true, false)) {
                    context.unbindService(connection)
                }
            }

            val timeout = Runnable {
                unbindIfNeeded()
                complete(
                    ScreeningQueryResult.Failure("The screening provider did not reply in time.")
                )
            }

            val replyMessenger = Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    mainHandler.removeCallbacks(timeout)
                    when (message.what) {
                        PublicSmsScreeningProtocol.messageScreeningResult -> {
                            val blocked = message.data?.getBoolean(
                                PublicSmsScreeningProtocol.keyBlocked,
                                false,
                            ) ?: false
                            unbindIfNeeded()
                            complete(
                                ScreeningQueryResult.Success(
                                    blocked = blocked,
                                    providerLabel = providerLabel,
                                )
                            )
                            true
                        }

                        PublicSmsScreeningProtocol.messageScreeningError -> {
                            val errorMessage = message.data?.getString(
                                PublicSmsScreeningProtocol.keyErrorMessage
                            ) ?: "The screening provider returned an unknown error."
                            unbindIfNeeded()
                            complete(ScreeningQueryResult.Failure(errorMessage))
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
                        PublicSmsScreeningProtocol.messageQueryShouldBlock,
                    ).apply {
                        replyTo = replyMessenger
                        data = Bundle().apply {
                            putString(PublicSmsScreeningProtocol.keyNumber, number)
                            putString(PublicSmsScreeningProtocol.keySmsContent, smsContent)
                            putInt(PublicSmsScreeningProtocol.keySimSlot, simSlot)
                        }
                    }

                    try {
                        Messenger(service).send(request)
                        mainHandler.postDelayed(timeout, 5_000)
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

            val explicitIntent = Intent(PublicSmsScreeningProtocol.action).apply {
                component = ComponentName(serviceInfo.packageName, serviceInfo.name)
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
                bound.set(true)
            } else if (!completed.get()) {
                complete(
                    ScreeningQueryResult.Failure("Failed to bind to the screening provider.")
                )
            }
        }
    }
}

private fun PackageManager.findPublicScreeningProvider(): ResolveInfo? {
    val intent = Intent(PublicSmsScreeningProtocol.action)
    val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        queryIntentServices(intent, 0)
    }

    return services.firstOrNull { it.serviceInfo?.exported == true }
}
