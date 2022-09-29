package com.copperleaf.ballast.navigation.routing

import com.copperleaf.ballast.BallastViewModelConfiguration
import com.copperleaf.ballast.core.BootstrapInterceptor
import com.copperleaf.ballast.core.FifoInputStrategy
import com.copperleaf.ballast.plusAssign
import io.ktor.http.encodeURLPath
import io.ktor.http.encodeURLQueryComponent

public val RouterContract.State.currentDestination: Destination?
    get() {
        return backstack
            .lastOrNull {
                it is Destination
            }
            as? Destination
    }

public val RouterContract.State.currentTag: Tag?
    get() {
        return backstack
            .lastOrNull {
                it is Tag
            }
            as? Tag
    }

public val RouterContract.State.currentDestinationOrNotFound: NavToken?
    get() {
        return backstack.lastOrNull {
            when (it) {
                is Destination -> true
                is MissingDestination -> true
                else -> false
            }
        }
    }

public fun Route.asStartDestination(): Destination {
    check(this.matcher.path.all { it is PathSegment.Static }) {
        "For a Route to be used as a Start Destination, it must be fully static " +
            "(no path parameters, wildcards, or tailcards)"
    }

    return this.matchDestinationOrThrow(originalRoute)
}

public fun Route.asStartDestinationString(): String {
    check(this.matcher.path.all { it is PathSegment.Static }) {
        "For a Route to be used as a Start Destination, it must be fully static " +
            "(no path parameters, wildcards, or tailcards)"
    }

    return this.originalRoute
}

public fun Route.asInitialBackstack(): List<Destination> {
    return listOf(this.asStartDestination())
}

public fun BallastViewModelConfiguration.Builder.withRouter(
    navGraph: NavGraph,
    initialRoute: Route = navGraph.routes.first(),
): BallastViewModelConfiguration.Builder =
    this
        .apply {
            this.inputStrategy = FifoInputStrategy()
            this.inputHandler = RouterInputHandler()
            this.initialState = RouterContract.State(navGraph = navGraph)
            this.name = "Router"

            this += BootstrapInterceptor {
                RouterContract.Inputs.GoToDestination(
                    initialRoute.asStartDestinationString()
                )
            }
        }

public fun BallastViewModelConfiguration.Builder.withRouter(
    routingTable: RoutingTable,
): BallastViewModelConfiguration.Builder =
    this
        .apply {
            this.inputStrategy = FifoInputStrategy()
            this.inputHandler = RouterInputHandler()
            this.initialState = RouterContract.State(navGraph = NavGraph(routingTable))
            this.name = "Router"

            this += BootstrapInterceptor {
                RouterContract.Inputs.GoToDestination(
                    routingTable.initialRoute.asStartDestinationString()
                )
            }
        }

public fun Map<String, List<String>>.formatQueryString() : String {
    return if (this.isNotEmpty()) {
        val duplicatedQueryParams: List<Pair<String, String>> = this.entries.flatMap { (key, values) ->
            values.map { value -> key to value }
        }

        "?" + duplicatedQueryParams.joinToString(separator = "&") { (key, value) ->
            val encodedKey = key.trim().encodeURLQueryComponent()
            val encodedValue = value.trim().encodeURLQueryComponent()
            "$encodedKey=$encodedValue"
        }
    } else {
        ""
    }
}

public fun Route.directions(
    pathParameters: Map<String, List<String>> = emptyMap(),
    queryParameters: Map<String, List<String>> = emptyMap(),
): String {
    // check that the provided path parameters exactly match what's needed in the route
    val extraKeys: Collection<String> = pathParameters.keys - this.matcher.path.mapNotNull { it.paramName }.toSet()

    check(extraKeys.isEmpty()) {
        "The following path parameter values could not be found in the directions to route '${this.originalRoute}': $extraKeys"
    }

    val formattedQueryString = queryParameters.formatQueryString()

    val formattedPath = this
        .matcher
        .path
        .mapNotNull {
            when (it) {
                is PathSegment.Static -> listOf(it.text)
                is PathSegment.Parameter -> {
                    val pathValue = if (!it.optional) {
                        checkNotNull(pathParameters[it.name]) {
                            "Non-optional path parameter '${it.name}' must be provided in destination to route '${this.originalRoute}'"
                        }
                    } else {
                        pathParameters[it.name]
                    }

                    pathValue?.single()?.encodeURLPath()?.let { listOf(it) }
                }
                is PathSegment.Wildcard -> {
                    error("Cannot create directions for wildcard path segment, consider switching to a named parameter instead in route '${this.originalRoute}'")
                }
                is PathSegment.Tailcard -> {
                    checkNotNull(it.name) {
                        "Cannot create directions for unnamed tailcard path segment, consider switching to a named tailcard instead in route '${this.originalRoute}'"
                    }
                    pathParameters[it.name]
                }
            }
        }
        .flatten()
        .map { it.trim() }
        .joinToString(separator = "/", prefix = "/")

    return "$formattedPath$formattedQueryString"
}


public fun goBack(
    currentState: RouterContract.State,
): RouterContract.State {
    return if (currentState.backstack.isEmpty()) {
        // error, backstack was empty
        currentState
    } else {
        val backstack = currentState.backstack.toMutableList()

        backstack.removeLast() as Destination
        if (backstack.lastOrNull() is Tag) {
            // remove the tag, too
            backstack.removeLast() as Tag
            backstack.lastOrNull { it is Tag } as? Tag
        }

        currentState.copy(backstack = backstack.toList())
    }
}

public fun addToTop(
    currentState: RouterContract.State,
    destination: String,
    tag: String?,
): RouterContract.State {
    return if (destination == currentState.currentDestination?.originalUrl) {
        // same as top destination, ignore it
        currentState
    } else {
        val matchedDestination = currentState.navGraph.findMatch(destination)

        val toAppendToBackstack: List<NavToken> = if (matchedDestination == null) {
            listOf(MissingDestination(destination))
        } else if (tag != null) {
            listOf(Tag(tag), matchedDestination)
        } else {
            listOf(matchedDestination)
        }


        currentState.copy(backstack = currentState.backstack.dropLastWhile { it is MissingDestination } + toAppendToBackstack)
    }

}

public fun NavToken.description(): String {
    return when (this) {
        is Destination -> "'${originalUrl}'"
        is MissingDestination -> "(Not Found)"
        is Tag -> "[${tag}]"
    }
}
