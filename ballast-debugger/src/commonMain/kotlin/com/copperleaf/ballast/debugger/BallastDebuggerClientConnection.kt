package com.copperleaf.ballast.debugger

import com.benasher44.uuid.uuid4
import com.copperleaf.ballast.BallastNotification
import com.copperleaf.ballast.debugger.models.BallastApplicationState
import com.copperleaf.ballast.debugger.models.BallastDebuggerAction
import com.copperleaf.ballast.debugger.models.BallastDebuggerEvent
import com.copperleaf.ballast.debugger.models.debuggerEventJson
import com.copperleaf.ballast.debugger.models.serialize
import com.copperleaf.ballast.debugger.models.updateConnection
import com.copperleaf.ballast.debugger.models.updateViewModel
import com.copperleaf.ballast.debugger.models.updateWithDebuggerEvent
import io.github.copper_leaf.ballast_debugger.BALLAST_VERSION
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.websocket.DefaultClientWebSocketSession
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.ExperimentalTime

@ExperimentalTime
public class BallastDebuggerClientConnection<out T : HttpClientEngineConfig>(
    engineFactory: HttpClientEngineFactory<T>,
    private val host: String = "127.0.0.1", // 10.1.1.20 on Android
    private val port: Int = 8080,
    private val connectionId: String = generateUuid(),
    block: HttpClientConfig<T>.() -> Unit = {}
) {
    public companion object {
        private fun generateUuid(): String {
            return uuid4().toString()
        }
    }

    private val client: HttpClient = HttpClient(engineFactory) {
        install(WebSockets)
        install(JsonFeature) {
            serializer = KotlinxSerializer()
        }
        block()
    }

    private class BallastDebuggerEventWrapper(
        val notification: BallastNotification<*, *, *>?,
        val debuggerEvent: BallastDebuggerEvent?,
        val updateConnectionState: Boolean,
    )

    private val outgoingMessages = Channel<BallastDebuggerEventWrapper>(Channel.UNLIMITED, BufferOverflow.SUSPEND)
    private var waitForEvent = CompletableDeferred<Unit>()

    private var applicationState: BallastApplicationState = BallastApplicationState()
    private val uuids: MutableMap<Any, String> = mutableMapOf()

    public fun CoroutineScope.connect(): Job {
        var failedAttempts = 0
        val job = launch(
            start = CoroutineStart.UNDISPATCHED,
            context = Dispatchers.Default,
        ) {
            while (true) {
                val currentTimeoutValue = when (failedAttempts) {
                    0 -> {
                        0L
                    }
                    in 1..10 -> {
                        1_000L
                    }
                    in 11..20 -> {
                        5_000L
                    }
                    in 21..30 -> {
                        30_000L
                    }
                    else -> Long.MAX_VALUE
                }

                try {
                    coroutineScope {

                        // either wait for a given timeout to reconnect, or if a new event comes in connect immediately
                        withTimeoutOrNull(currentTimeoutValue) {
                            waitForEvent.await()
                        }

                        client.webSocket(
                            method = HttpMethod.Get,
                            host = host,
                            port = port,
                            path = "/ballast/debugger",
                            request = {
                                header("x-ballast-connection-id", connectionId)
                                header("x-ballast-version", BALLAST_VERSION)
                            }
                        ) {
                            println("Connected to Ballast debugger: $connectionId")
                            failedAttempts = 0
                            waitForEvent = CompletableDeferred()
                            joinAll(
                                heartbeat(),
                                processOutgoing(),
                                processIncoming(),
                            )
                        }
                    }
                    failedAttempts++
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    failedAttempts++
                }
            }
        }
        return job
    }

    internal suspend fun acceptNotification(notification: BallastNotification<*, *, *>) {
        outgoingMessages.send(
            BallastDebuggerEventWrapper(
                notification = notification,
                debuggerEvent = null,
                updateConnectionState = true,
            )
        )
        waitForEvent.complete(Unit)
    }

// Impl
// ---------------------------------------------------------------------------------------------------------------------

    private fun getUuid(notification: BallastNotification<*, *, *>): String {
        return when (notification) {
            is BallastNotification.InputQueued -> {
                uuids.getOrPut(notification.input) { generateUuid() }
            }
            is BallastNotification.InputAccepted -> {
                uuids.getOrPut(notification.input) { generateUuid() }
            }
            is BallastNotification.InputRejected -> {
                uuids.getOrPut(notification.input) { generateUuid() }
            }
            is BallastNotification.InputDropped -> {
                uuids.getOrPut(notification.input) { generateUuid() }
            }
            is BallastNotification.InputHandledSuccessfully -> {
                uuids.remove(notification.input) ?: generateUuid()
            }
            is BallastNotification.InputCancelled -> {
                uuids.remove(notification.input) ?: generateUuid()
            }
            is BallastNotification.InputHandlerError -> {
                uuids.remove(notification.input) ?: generateUuid()
            }

            is BallastNotification.EventQueued -> {
                uuids.getOrPut(notification.event) { generateUuid() }
            }
            is BallastNotification.EventEmitted -> {
                uuids.getOrPut(notification.event) { generateUuid() }
            }
            is BallastNotification.EventHandledSuccessfully -> {
                uuids.remove(notification.event) ?: generateUuid()
            }
            is BallastNotification.EventHandlerError -> {
                uuids.remove(notification.event) ?: generateUuid()
            }

            is BallastNotification.SideEffectStarted -> {
                uuids.getOrPut(notification.key) { generateUuid() }
            }
            is BallastNotification.SideEffectCompleted -> {
                uuids.remove(notification.key) ?: generateUuid()
            }
            is BallastNotification.SideEffectCancelled -> {
                uuids.remove(notification.key) ?: generateUuid()
            }
            is BallastNotification.SideEffectError -> {
                uuids.remove(notification.key) ?: generateUuid()
            }

            else -> generateUuid()
        }
    }

    /**
     * send a heartbeat from this connection every 5 seconds, so the UI can show when each connection is still alive
     */
    private fun DefaultClientWebSocketSession.heartbeat(): Job {
        val session = this

        return flow<Unit> {
            while (true) {
                emit(Unit)
            }
        }
            .onEach { delay(5000) }
            .onEach {
                session.send(
                    debuggerEventJson
                        .encodeToString(
                            BallastDebuggerEvent.serializer(),
                            BallastDebuggerEvent.Heartbeat(connectionId)
                        )
                        .let { Frame.Text(it) }
                )
            }
            .launchIn(this)
    }

    /**
     * send messages from the channel to the websocket
     */
    private fun DefaultClientWebSocketSession.processOutgoing(): Job {
        val session = this
        return outgoingMessages
            .receiveAsFlow()
            .onEach { message ->

                val event = if (message.notification != null) {
                    check(message.debuggerEvent == null) { "Must provide a notification or a debugger event, not both" }

                    val uuid = getUuid(message.notification)
                    message.notification.serialize(connectionId, uuid)
                } else if (message.debuggerEvent != null) {
                    message.debuggerEvent
                } else {
                    error("Must provide a notification or a debugger event, not both")
                }

                if (message.updateConnectionState) {
                    // update the local cache with the same event the client UI has, so we can request refreshed data if
                    // something has been dropped
                    applicationState = applicationState.updateConnection(connectionId) {
                        updateViewModel(event.viewModelName) {
                            updateWithDebuggerEvent(event)
                        }
                    }
                }

                // send the message through the websocket to the client UI, where it will be processed in the same way
                session.send(
                    debuggerEventJson
                        .encodeToString(BallastDebuggerEvent.serializer(), event)
                        .let { Frame.Text(it) }
                )
            }
            .launchIn(this)
    }

    /**
     * read messages sent from the debugger UI to the app
     */
    private fun DefaultClientWebSocketSession.processIncoming(): Job {
        return incoming
            .receiveAsFlow()
            .filterIsInstance<Frame.Text>()
            .onEach { frame ->
                val text = frame.readText()

                debuggerEventJson
                    .decodeFromString(BallastDebuggerAction.serializer(), text)
                    .let { handleAction(it) }
            }
            .launchIn(this)
    }

    private suspend fun DefaultClientWebSocketSession.handleAction(action: BallastDebuggerAction) {
        return when (action) {
            is BallastDebuggerAction.RequestViewModelRefresh -> {
                val currentViewModelHistory = applicationState.connections
                    .firstOrNull { it.connectionId == connectionId }
                    ?.viewModels
                    ?.firstOrNull { it.viewModelName == action.viewModelName }
                    ?.fullHistory
                    ?.reversed()

                if (currentViewModelHistory != null) {
                    outgoingMessages.send(
                        BallastDebuggerEventWrapper(
                            notification = null,
                            debuggerEvent = BallastDebuggerEvent.RefreshViewModelStart(
                                connectionId,
                                action.viewModelName,
                            ),
                            updateConnectionState = false,
                        )
                    )

                    currentViewModelHistory.forEach {
                        outgoingMessages.send(
                            BallastDebuggerEventWrapper(
                                notification = null,
                                debuggerEvent = it,
                                updateConnectionState = false,
                            )
                        )
                    }

                    outgoingMessages.send(
                        BallastDebuggerEventWrapper(
                            notification = null,
                            debuggerEvent = BallastDebuggerEvent.RefreshViewModelComplete(
                                connectionId,
                                action.viewModelName
                            ),
                            updateConnectionState = false,
                        )
                    )

                    waitForEvent.complete(Unit)
                }

                Unit
            }
        }
    }
}
