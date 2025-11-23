//Реестр «здоровья» нод + минутный опрос
package com.customDB.server

import com.customDB.api.NodeRef
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class NodeHealth(val lastOkMillis: Long, val lastCode: Int)

object HealthRegistry : ClusterListener {
    private val health = ConcurrentHashMap<NodeRef, NodeHealth>()
    private const val TIMEOUT_MS = 1500
    private const val DOWN_THRESHOLD_MS = 90_000 // считаем «упала», если не ок >90с

    override fun onClusterChanged(state: ClusterState) {
        //сам возьмет свежие ноды из ClusterBus.current()
    }

    fun startPolling(periodSec: Long = 60) {
        val pool = Executors.newSingleThreadScheduledExecutor()
        pool.scheduleAtFixedRate({
            val now = System.currentTimeMillis()
            val seen = mutableSetOf<NodeRef>()
            val st = ClusterBus.current()
            st.cfg.shards.forEach { sh ->
                val nodes = buildList {
                    add(sh.master)
                    addAll(sh.replicas)
                }
                nodes.forEach { n ->
                    seen += n
                    val ok = ping(n)
                    if (ok != null) health[n] = ok.copy(lastOkMillis = if (ok.lastCode in 200..299) now else (health[n]?.lastOkMillis ?: 0L))
                }
            }
            // зачистка удалённых нод
            health.keys.retainAll(seen)
        }, 0, periodSec, TimeUnit.SECONDS)
        ClusterBus.subscribe(this)
    }

    private fun ping(n: NodeRef): NodeHealth? {
        return try {
            val url = URL("http://${n.host}:${n.port}/health")
            val c = (url.openConnection() as HttpURLConnection).apply { connectTimeout = TIMEOUT_MS; readTimeout = TIMEOUT_MS }
            c.inputStream.use { _ -> } // достаточно, что ответили
            NodeHealth(System.currentTimeMillis(), c.responseCode)
        } catch (_: Throwable) { NodeHealth(health[n]?.lastOkMillis ?: 0L, 0) }
    }

    fun isAlive(n: NodeRef): Boolean {
        val h = health[n] ?: return false
        val age = System.currentTimeMillis() - h.lastOkMillis
        return h.lastOkMillis > 0 && age < DOWN_THRESHOLD_MS
    }
}
