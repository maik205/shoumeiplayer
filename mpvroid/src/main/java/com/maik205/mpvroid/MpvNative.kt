package com.maik205.mpvroid

import android.content.Context
import android.view.Surface
import java.util.concurrent.CopyOnWriteArrayList

/**
 * JNI facade over mpv's public client API.
 *
 * `libmpv.so` must be built from the pinned official mpv source described in
 * `native/mpv/README.md`; no Java/Kotlin binding or native AAR is involved.
 */
object MpvNative {
    init {
        System.loadLibrary("mpv")
        System.loadLibrary("mpvroid_jni")
    }

    private val eventObservers = CopyOnWriteArrayList<EventObserver>()
    private val logObservers = CopyOnWriteArrayList<LogObserver>()

    external fun create(context: Context)
    external fun init()
    external fun destroy()
    external fun attachSurface(surface: Surface)
    external fun detachSurface()
    external fun command(command: Array<String>)
    external fun setOptionString(name: String, value: String): Int
    external fun getPropertyString(name: String): String?
    external fun setPropertyString(name: String, value: String)
    external fun setPropertyBoolean(name: String, value: Boolean)
    external fun setPropertyDouble(name: String, value: Double)
    external fun observeProperty(name: String, format: Int)

    fun addObserver(observer: EventObserver) {
        eventObservers += observer
    }

    fun removeObserver(observer: EventObserver) {
        eventObservers -= observer
    }

    fun addLogObserver(observer: LogObserver) {
        logObservers += observer
    }

    fun removeLogObserver(observer: LogObserver) {
        logObservers -= observer
    }

    private fun eventProperty(name: String) =
        eventObservers.forEach { it.eventProperty(name) }

    private fun eventPropertyLong(name: String, value: Long) =
        eventObservers.forEach { it.eventProperty(name, value) }

    private fun eventPropertyDouble(name: String, value: Double) =
        eventObservers.forEach { it.eventProperty(name, value) }

    private fun eventPropertyFlag(name: String, value: Boolean) =
        eventObservers.forEach { it.eventProperty(name, value) }

    private fun eventPropertyString(name: String, value: String) =
        eventObservers.forEach { it.eventProperty(name, value) }

    private fun event(id: Int) =
        eventObservers.forEach { it.event(id) }

    private fun logMessage(prefix: String, level: Int, text: String) =
        logObservers.forEach { it.logMessage(prefix, level, text) }

    interface EventObserver {
        fun eventProperty(property: String)
        fun eventProperty(property: String, value: Long)
        fun eventProperty(property: String, value: Double)
        fun eventProperty(property: String, value: Boolean)
        fun eventProperty(property: String, value: String)
        fun event(eventId: Int)
    }

    interface LogObserver {
        fun logMessage(prefix: String, level: Int, text: String)
    }

    const val MPV_FORMAT_NONE = 0
    const val MPV_FORMAT_STRING = 1
    const val MPV_FORMAT_FLAG = 3
    const val MPV_FORMAT_INT64 = 4
    const val MPV_FORMAT_DOUBLE = 5

    const val MPV_EVENT_SHUTDOWN = 1
    const val MPV_EVENT_END_FILE = 7
    const val MPV_EVENT_FILE_LOADED = 8
    const val MPV_EVENT_SEEK = 20
    const val MPV_EVENT_PLAYBACK_RESTART = 21

    const val MPV_LOG_LEVEL_NONE = 0
    const val MPV_LOG_LEVEL_ERROR = 20
}
