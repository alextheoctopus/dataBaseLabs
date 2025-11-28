package com.customDB.server

import com.customDB.api.ClusterCfg
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.serialization.json.Json

typealias ClusterListener = (prev: ClusterCfg?, cur: ClusterCfg) -> Unit

object ClusterBus {
    private val state = AtomicReference<ClusterState?>()
    private val listeners = mutableListOf<ClusterListener>()
    @Volatile private var watcherStarted = false

    fun current(): ClusterState =
        state.get() ?: error("ClusterBus is not initialized")

    fun addListener(l: ClusterListener) {
        synchronized(listeners) { listeners += l }
        // если состояние уже есть — сразу уведомим нового слушателя
        state.get()?.let { l(null, it.cfg) }
    }

    private fun notify(prev: ClusterCfg?, cur: ClusterCfg) {
        val copy = synchronized(listeners) { listeners.toList() }
        copy.forEach { it(prev, cur) }
    }

    fun initAndWatch(
        clusterFile: File,
        pollMs: Long = 1000,
        onChange: ClusterListener? = null
    ) {
        if (onChange != null) addListener(onChange)
        if (watcherStarted) return
        watcherStarted = true

        fun loadIfChanged(prevCfg: ClusterCfg?): ClusterCfg {
            val text = clusterFile.readText()
            val cfg = Json.decodeFromString(ClusterCfg.serializer(), text)
            if (prevCfg == null || prevCfg != cfg) {
                val prev = state.get()?.cfg
                state.set(ClusterState(cfg))
                println("[ClusterBus] reloaded: ${clusterFile.absolutePath}")
                notify(prev, cfg)
            }
            return cfg
        }

        // начальная загрузка
        loadIfChanged(null)

        thread(name = "cluster-watch", isDaemon = true) {
            var last: ClusterCfg? = state.get()?.cfg
            var lastMtime = clusterFile.lastModified()
            while (true) {
                try {
                    val m = clusterFile.lastModified()
                    if (m != lastMtime) {
                        lastMtime = m
                        last = loadIfChanged(last)
                    }
                    Thread.sleep(pollMs)
                } catch (_: Throwable) { Thread.sleep(pollMs) }
            }
        }
    }
}