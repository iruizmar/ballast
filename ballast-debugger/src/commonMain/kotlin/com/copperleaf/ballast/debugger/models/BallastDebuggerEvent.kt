package com.copperleaf.ballast.debugger.models

import com.copperleaf.ballast.SideEffectScope
import com.copperleaf.ballast.debugger.utils.now
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
public sealed class BallastDebuggerEvent {
    public abstract val connectionId: String
    public abstract val viewModelName: String?
    public abstract val uuid: String?

    public val timestamp: LocalDateTime = LocalDateTime.now()

// Events not from INterceptor, but used for app internal communication
// ---------------------------------------------------------------------------------------------------------------------

    /**
     * Send a heartbeat from the device so the UI can detect when it is active and when the connection has died
     */
    @Serializable
    public class Heartbeat(
        override val connectionId: String,
    ) : BallastDebuggerEvent() {
        override val viewModelName: String? = null
        override val uuid: String? = null
    }

    /**
     * Clear all data in the given ViewModel, so the device can re-send the entire history and bring it back to the
     * proper status in the UI, and set the flag to denote that a full refresh is underway.
     */
    @Serializable
    public class RefreshViewModelStart(
        override val connectionId: String,
        override val viewModelName: String,
    ) : BallastDebuggerEvent() {
        override val uuid: String? = null
    }

    /**
     * Clear the flag to denote that a full refresh is underway.
     */
    @Serializable
    public class RefreshViewModelComplete(
        override val connectionId: String,
        override val viewModelName: String,
    ) : BallastDebuggerEvent() {
        override val uuid: String? = null
    }

// ViewModel
// ---------------------------------------------------------------------------------------------------------------------

    @Serializable
    public class ViewModelStarted(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,
    ) : BallastDebuggerEvent()

    @Serializable
    public class ViewModelCleared(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,
    ) : BallastDebuggerEvent()

// Inputs
// ---------------------------------------------------------------------------------------------------------------------

    @Serializable
    public class InputQueued(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val inputType: String,
        public val inputToStringValue: String,
    ) : BallastDebuggerEvent()

    @Serializable
    public class InputAccepted(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val inputType: String,
        public val inputToStringValue: String,
    ) : BallastDebuggerEvent()

    @Serializable
    public class InputRejected(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val inputType: String,
        public val inputToStringValue: String,
    ) : BallastDebuggerEvent()

    @Serializable
    public class InputDropped(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val inputType: String,
        public val inputToStringValue: String,
    ) : BallastDebuggerEvent()

    @Serializable
    public class InputHandledSuccessfully(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val inputType: String,
        public val inputToStringValue: String,
    ) : BallastDebuggerEvent()

    @Serializable
    public class InputCancelled(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val inputType: String,
        public val inputToStringValue: String,
    ) : BallastDebuggerEvent()

    @Serializable
    public class InputHandlerError(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val inputType: String,
        public val inputToStringValue: String,
        public val stacktrace: String,
    ) : BallastDebuggerEvent()

// Events
// ---------------------------------------------------------------------------------------------------------------------

    @Serializable
    public class EventQueued(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val eventType: String,
        public val eventToStringValue: String,
    ) : BallastDebuggerEvent()

    @Serializable
    public class EventEmitted(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val eventType: String,
        public val eventToStringValue: String,
    ) : BallastDebuggerEvent()

    @Serializable
    public class EventHandledSuccessfully(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val eventType: String,
        public val eventToStringValue: String,
    ) : BallastDebuggerEvent()

    @Serializable
    public class EventHandlerError(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val eventType: String,
        public val eventToStringValue: String,
        public val stacktrace: String,
    ) : BallastDebuggerEvent()

    @Serializable
    public class EventProcessingStarted(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,
    ) : BallastDebuggerEvent()

    @Serializable
    public class EventProcessingStopped(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,
    ) : BallastDebuggerEvent()

// States
// ---------------------------------------------------------------------------------------------------------------------

    @Serializable
    public class StateChanged(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val stateType: String,
        public val stateToStringValue: String,
    ) : BallastDebuggerEvent()

// Side Effects
// ---------------------------------------------------------------------------------------------------------------------

    @Serializable
    public class SideEffectStarted(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val key: String,
        public val restartState: SideEffectScope.RestartState,
    ) : BallastDebuggerEvent()

    @Serializable
    public class SideEffectCompleted(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val key: String,
        public val restartState: SideEffectScope.RestartState,
    ) : BallastDebuggerEvent()

    @Serializable
    public class SideEffectCancelled(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val key: String,
        public val restartState: SideEffectScope.RestartState,
    ) : BallastDebuggerEvent()

    @Serializable
    public class SideEffectError(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val key: String,
        public val restartState: SideEffectScope.RestartState,
        public val stacktrace: String,
    ) : BallastDebuggerEvent()

// Other
// ---------------------------------------------------------------------------------------------------------------------

    @Serializable
    public class UnhandledError(
        override val connectionId: String,
        override val viewModelName: String,
        override val uuid: String,

        public val stacktrace: String,
    ) : BallastDebuggerEvent()
}
