package com.netspeedtest.ui.nav

/** Every destination in the app. */
sealed class Route {
    object Home : Route()
    /** A stored result, identified by its timestamp (history entries are unique by time). */
    data class Result(val timestampMillis: Long, val fresh: Boolean = false) : Route()
    object History : Route()
    object Settings : Route()
    object Battery : Route()
    object Charging : Route()
    object Temperature : Route()
    object Network : Route()
    object Memory : Route()
}

/**
 * The whole navigation model: a back stack of [Route]s. It lives in the application
 * graph, so it survives configuration changes along with the running test.
 */
class Navigator {
    private val stack = ArrayList<Route>().apply { add(Route.Home) }

    /** Called with the new top route and whether the move was forward (push) or back. */
    var listener: ((route: Route, forward: Boolean) -> Unit)? = null

    val current: Route get() = stack.last()
    val depth: Int get() = stack.size
    val canGoBack: Boolean get() = stack.size > 1

    fun push(route: Route) {
        if (route == current) return
        stack += route
        listener?.invoke(route, true)
    }

    /** Replaces the top of the stack (e.g. a result screen that starts a new test). */
    fun replaceWithHome() {
        if (stack.size == 1) return
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        listener?.invoke(current, false)
    }

    fun pop(): Boolean {
        if (!canGoBack) return false
        stack.removeAt(stack.lastIndex)
        listener?.invoke(current, false)
        return true
    }
}
