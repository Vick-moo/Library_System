package com.example.librarysystem.data

/**
 * One place to turn a thrown exception into something a librarian can read.
 *
 * Supabase / Ktor exceptions carry the project URL, the PostgREST path and
 * sometimes the API key in `message`, so that string must never reach the UI.
 */
object ErrorText {

    const val OFFLINE = "Connection lost. Please check your connection and try again."
    const val LOAD_FAILED = "Could not load data. Pull down to try again."

    /** True when the failure is a dead network rather than a rejected request. */
    fun isNetwork(e: Throwable?): Boolean {
        var t = e
        var depth = 0
        while (t != null && depth < 8) {
            when (t) {
                is java.net.UnknownHostException,
                is java.net.ConnectException,
                is java.net.SocketTimeoutException,
                is java.net.NoRouteToHostException,
                is java.net.PortUnreachableException,
                is java.io.InterruptedIOException,
                is java.nio.channels.UnresolvedAddressException -> return true
            }
            val m = t.message?.lowercase().orEmpty()
            if (m.contains("unable to resolve host") ||
                m.contains("failed to connect") ||
                m.contains("connection refused") ||
                m.contains("connection reset") ||
                m.contains("network is unreachable") ||
                m.contains("no address associated with hostname") ||
                m.contains("connection timed out") ||
                m.contains("connect timeout") ||
                m.contains("socket timeout")
            ) return true
            t = t.cause
        }
        return false
    }

    /** Standard catch-all for a failed read. */
    fun loadError(e: Throwable?) = if (isNetwork(e)) OFFLINE else LOAD_FAILED
}
