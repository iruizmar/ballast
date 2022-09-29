package com.copperleaf.ballast.examples.util

import com.copperleaf.ballast.BallastInterceptor
import com.copperleaf.ballast.BallastLogger
import com.copperleaf.ballast.BallastViewModelConfiguration
import com.copperleaf.ballast.InputStrategy
import com.copperleaf.ballast.build
import com.copperleaf.ballast.core.LoggingInterceptor
import com.copperleaf.ballast.debugger.BallastDebuggerClientConnection
import com.copperleaf.ballast.examples.bgg.api.BggApi
import com.copperleaf.ballast.examples.bgg.repository.BggRepositoryImpl
import com.copperleaf.ballast.examples.bgg.ui.BggContract
import com.copperleaf.ballast.examples.bgg.ui.BggInputHandler
import com.copperleaf.ballast.examples.counter.CounterContract
import com.copperleaf.ballast.examples.counter.CounterInputHandler
import com.copperleaf.ballast.examples.kitchensink.KitchenSinkContract
import com.copperleaf.ballast.examples.kitchensink.KitchenSinkInputHandler
import com.copperleaf.ballast.examples.kitchensink.KitchenSinkViewModel
import com.copperleaf.ballast.examples.kitchensink.controller.KitchenSinkControllerContract
import com.copperleaf.ballast.examples.kitchensink.controller.KitchenSinkControllerInputHandler
import com.copperleaf.ballast.examples.kitchensink.controller.KitchenSinkControllerSavedStateAdapter
import com.copperleaf.ballast.examples.mainlist.MainContract
import com.copperleaf.ballast.examples.mainlist.MainInputHandler
import com.copperleaf.ballast.examples.navigation.Routes
import com.copperleaf.ballast.examples.scorekeeper.ScorekeeperContract
import com.copperleaf.ballast.examples.scorekeeper.ScorekeeperInputHandler
import com.copperleaf.ballast.examples.scorekeeper.ScorekeeperSavedStateAdapter
import com.copperleaf.ballast.navigation.routing.RouterContract
import com.copperleaf.ballast.navigation.routing.withRouter
import com.copperleaf.ballast.plusAssign
import com.copperleaf.ballast.repository.bus.EventBusImpl
import com.copperleaf.ballast.savedstate.BallastSavedStateInterceptor
import com.copperleaf.ballast.savedstate.SavedStateAdapter
import com.copperleaf.ballast.sync.BallastSyncInterceptor
import com.copperleaf.ballast.sync.DefaultSyncConnection
import com.copperleaf.ballast.sync.InMemorySyncAdapter
import com.copperleaf.ballast.sync.SyncClientType
import com.copperleaf.ballast.sync.SyncConnection
import com.copperleaf.ballast.withViewModel
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.CoroutineScope

abstract class CommonBallastInjector<out T : HttpClientEngineConfig>(
    private val applicationScope: CoroutineScope,
    private val engineFactory: HttpClientEngineFactory<T>,
    private val bggApi: (HttpClient) -> BggApi,
    private val loggerFactory: (String) -> BallastLogger,
    private val debuggerHost: String,
) {
    @OptIn(com.russhwolf.settings.ExperimentalSettingsImplementation::class)
    private val preferences = ExamplesPreferencesImpl(Settings())
    private val httpClient = HttpClient(engineFactory) {
        install(Logging) {
            level = LogLevel.BODY
            logger = object : Logger {
                override fun log(message: String) {
                    loggerFactory("Ktor").info(message)
                }
            }
        }
    }
    private val debuggerConnection by lazy {
        BallastDebuggerClientConnection(
            engineFactory = engineFactory,
            applicationCoroutineScope = applicationScope,
            host = debuggerHost,
        ).also {
            it.connect(loggerFactory("Debugger"))
        }
    }
    private val eventBus = EventBusImpl()
    private val bggRepository by lazy {
        BggRepositoryImpl(
            coroutineScope = applicationScope,
            eventBus = eventBus,
            configBuilder = commonBuilder(),
            api = bggApi(httpClient),
        )
    }

    private val counterSyncConnection: SyncConnection<
        CounterContract.Inputs,
        CounterContract.Events,
        CounterContract.State> by lazy {
        DefaultSyncConnection(
            InMemorySyncAdapter()
        )
    }

    protected fun commonBuilder(): BallastViewModelConfiguration.Builder {
        return BallastViewModelConfiguration.Builder()
            .apply {
                this += LoggingInterceptor()
//                this += BallastDebuggerInterceptor(debuggerConnection)
                logger = loggerFactory
            }
    }

    protected fun routerConfiguration(
        vararg additionalInterceptors: BallastInterceptor<RouterContract.Inputs,
            RouterContract.Events,
            RouterContract.State>,
    ): BallastViewModelConfiguration<
        RouterContract.Inputs,
        RouterContract.Events,
        RouterContract.State> = commonBuilder()
        .withRouter(routingTable = Routes)
        .apply { this += additionalInterceptors.toList() }
        .build()

    protected fun mainConfiguration(): BallastViewModelConfiguration<
        MainContract.Inputs,
        MainContract.Events,
        MainContract.State> = commonBuilder()
        .withViewModel(
            initialState = MainContract.State(),
            inputHandler = MainInputHandler(),
            name = "Main",
        )
        .build()

    protected fun kitchenSinkControllerConfiguration(): BallastViewModelConfiguration<
        KitchenSinkControllerContract.Inputs,
        KitchenSinkControllerContract.Events,
        KitchenSinkControllerContract.State> = commonBuilder()
        .apply {
            this += BallastSavedStateInterceptor(
                KitchenSinkControllerSavedStateAdapter(preferences)
            )
        }
        .withViewModel(
            initialState = KitchenSinkControllerContract.State(),
            inputHandler = KitchenSinkControllerInputHandler(::kitchenSinkViewModel),
            name = "KitchenSink Controller",
        )
        .build()

    abstract fun kitchenSinkViewModel(
        coroutineScope: CoroutineScope, inputStrategy: InputStrategy<*, *, *>
    ): KitchenSinkViewModel

    protected fun kitchenSinkConfiguration(
        inputStrategy: InputStrategy<*, *, *>,
    ): BallastViewModelConfiguration<
        KitchenSinkContract.Inputs,
        KitchenSinkContract.Events,
        KitchenSinkContract.State> = commonBuilder()
        .apply {
            this.inputStrategy = inputStrategy
        }
        .withViewModel(
            initialState = KitchenSinkContract.State(),
            inputHandler = KitchenSinkInputHandler(),
            name = "KitchenSink",
        )
        .build()

    protected fun counterConfiguration(
        syncClientType: SyncClientType,
        adapter: SavedStateAdapter<
            CounterContract.Inputs,
            CounterContract.Events,
            CounterContract.State>?,
    ): BallastViewModelConfiguration<
        CounterContract.Inputs,
        CounterContract.Events,
        CounterContract.State> = commonBuilder()
        .apply {
            this += BallastSyncInterceptor(
                connection = counterSyncConnection,
                clientType = syncClientType,
            )
            if (adapter != null) {
                this += BallastSavedStateInterceptor(adapter)
            }
        }
        .withViewModel(
            initialState = CounterContract.State(),
            inputHandler = CounterInputHandler(),
            name = "Counter",
        )
        .build()

    protected fun bggConfiguration(): BallastViewModelConfiguration<
        BggContract.Inputs,
        BggContract.Events,
        BggContract.State> = commonBuilder()
        .withViewModel(
            initialState = BggContract.State(),
            inputHandler = BggInputHandler(bggRepository),
            name = "BGG",
        )
        .build()

    protected fun scorekeeperConfiguration(): BallastViewModelConfiguration<
        ScorekeeperContract.Inputs,
        ScorekeeperContract.Events,
        ScorekeeperContract.State> = commonBuilder()
        .apply {
            this += BallastSavedStateInterceptor(
                ScorekeeperSavedStateAdapter(preferences)
            )
        }
        .withViewModel(
            initialState = ScorekeeperContract.State(),
            inputHandler = ScorekeeperInputHandler(),
            name = "Scorekeeper",
        )
        .build()
}
