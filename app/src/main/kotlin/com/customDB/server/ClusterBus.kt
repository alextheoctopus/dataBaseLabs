package com.customDB.server
//Общая «шина» кластера + авто-перечитывание cluster.json
//Все компоненты читают кластер через ClusterBus.current() и/или подписываются на изменения.
//Файл пере-читается каждые 10 сек; при изменении дергаются слушатели.
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

interface ClusterListener {
    fun onClusterChanged(state: ClusterState)
}

object ClusterBus {
    @Volatile private var state: ClusterState? = null
    private val listeners = CopyOnWriteArrayList<ClusterListener>()

    fun current(): ClusterState = state
        ?: error("ClusterBus not initialized")

    fun subscribe(l: ClusterListener) { listeners += l; state?.let { l.onClusterChanged(it) } }

    fun initAndWatch(clusterFile: File, periodSec: Long = 10) {
        var lastMtime = 0L
        fun loadIfChanged() {
            if (!clusterFile.exists()) return
            val m = clusterFile.lastModified()
            if (state == null || m != lastMtime) {
                val newState = ClusterState.load(clusterFile)
                state = newState
                lastMtime = m
                listeners.forEach { it.onClusterChanged(newState) }
                println("[ClusterBus] reloaded: ${clusterFile.absolutePath}")
            }
        }
        loadIfChanged()
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
            { kotlin.runCatching { loadIfChanged() } },
            periodSec, periodSec, TimeUnit.SECONDS
        )
    }
}
