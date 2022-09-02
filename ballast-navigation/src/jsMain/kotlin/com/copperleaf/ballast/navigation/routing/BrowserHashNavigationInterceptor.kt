package com.copperleaf.ballast.navigation.routing

import com.copperleaf.ballast.BallastNotification
import com.copperleaf.ballast.Queued
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.w3c.dom.HashChangeEvent
import org.w3c.dom.events.Event

public class BrowserHashNavigationInterceptor : RouterInterceptor {

    override fun RouterInterceptorScope.start(notifications: RouterNotifications) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            // start by setting the initial route from the browser hash, if provided when the webpage first loads
            onViewModelInitSetStateFromBrowserHash(notifications)

            // then sync the hash state to the router state (in both directions)
            joinAll(
                updateBrowserHashOnStateChange(notifications),
                updateStateOnBrowserHashChange(notifications)
            )
        }
    }

    private fun RouterInterceptorScope.updateBrowserHashOnStateChange(
        notifications: RouterNotifications
    ): Job = launch(start = CoroutineStart.UNDISPATCHED) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            notifications
                .filterIsInstance<BallastNotification.EventEmitted<
                    RouterContract.Inputs,
                    RouterContract.Events,
                    RouterContract.State,
                    >>()
                .mapNotNull { it.event as? RouterContract.Events.NewDestination }
                .filter { it.token == null || it.token !is HashChangeEvent }
                .mapNotNull {
                    when (val destination = it.currentDestinationOrNotFound) {
                        is Destination -> {
                            buildString {
                                append(destination.path)
                                if (destination.queryParameters.isNotEmpty()) {
                                    append('?')
                                    append(destination.queryParameters.formatQueryString())
                                }
                            }
                        }

                        is MissingDestination -> destination.originalUrl
                        else -> null
                    }
                }
                .distinctUntilChanged()
                .onEach {
                    window.location.hash = it
                    window.location.search = ""
                }
                .launchIn(this)
        }
    }

    private fun RouterInterceptorScope.updateStateOnBrowserHashChange(
        notifications: RouterNotifications
    ): Job = launch(start = CoroutineStart.UNDISPATCHED) {
        val hashChangeEventAsFlow = callbackFlow {
            val callback = { event: Event ->
                if (event is HashChangeEvent) {
                    this@callbackFlow.trySend(event)
                }

                Unit
            }
            window.addEventListener("hashchange", callback)

            awaitClose {
                window.removeEventListener("hashchange", callback)
            }
        }

        hashChangeEventAsFlow
            .onEach { ev ->
                sendToQueue(
                    Queued.HandleInput(
                        null,
                        RouterContract.Inputs.GoToDestination(
                            destination = ev.newURL.split("#").last(),
                            token = ev,
                        )
                    )
                )
            }
            .launchIn(this)
    }

    private suspend fun RouterInterceptorScope.onViewModelInitSetStateFromBrowserHash(
        notifications: RouterNotifications
    ) {
        // wait for the BallastNotification.ViewModelStarted to be sent
        notifications
            .filterIsInstance<BallastNotification.ViewModelStarted<
                RouterContract.Inputs,
                RouterContract.Events,
                RouterContract.State,
                >>()
            .first()

        val currentPath = getCurrentPath()
        val currentQueryString = getCurrentQueryString()
        if (currentPath.isNotBlank() || !currentQueryString.isNullOrBlank()) {
            console.log("initializing from hash: $currentPath (query: $currentQueryString)")
            console.log(window.location)

            val initialDestination = buildString {
                append('/')
                append(currentPath)
                if (!currentQueryString.isNullOrBlank()) {
                    append('?')
                    append(currentQueryString)
                }
            }

            // initialize the VM with the current browser hash
            sendToQueue(
                Queued.HandleInput(
                    null,
                    RouterContract.Inputs.GoToDestination(
                        destination = initialDestination
                    )
                )
            )

            // wait for the current hash to be set in the state, before allowing the rest of the hash-syncing to begin
            notifications
                .filterIsInstance<BallastNotification.StateChanged<
                    RouterContract.Inputs,
                    RouterContract.Events,
                    RouterContract.State,
                    >>()
                .first { it.state.currentDestination?.originalUrl == initialDestination }
        }
    }

    private fun getCurrentPath(): String {
        return window.location.hash.trimStart('#').trimStart('/').split('?').first()
    }

    private fun getCurrentQueryString(): String? {
        return window.location.search.takeIf { it.isNotBlank() }?.trimStart('?')
            ?: window.location.hash.trimStart('#').split('?').getOrNull(1)
    }
}
