package com.phoneproof.core.diagnostics

/**
 * Process-wide access to the recorder.
 *
 * A plain singleton rather than something injected, for one specific reason: the first thing this
 * has to survive is an uncaught exception on an arbitrary thread during application startup. At that
 * moment a dependency graph may not exist yet, and a diagnostics tool that needs the app to be
 * healthy in order to report that the app is unhealthy is useless.
 */
object Diagnostics {

    val recorder: DiagnosticsRecorder = DiagnosticsRecorder()

    fun info(tag: String, message: String) = recorder.info(tag, message)

    fun warn(tag: String, message: String, error: Throwable? = null) =
        recorder.warn(tag, message, error)

    fun error(tag: String, message: String, error: Throwable? = null) =
        recorder.error(tag, message, error)

    /**
     * Records an uncaught exception, then hands control to whatever handler was already installed so
     * the normal crash path — including Play Console reporting — still happens. Swallowing the
     * exception here would hide crashes from the one place that aggregates them.
     */
    fun installCrashHandler(tag: String = "uncaught") {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                recorder.crash(tag, "uncaught on thread '${thread.name}'", throwable)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
