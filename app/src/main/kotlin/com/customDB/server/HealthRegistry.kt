// app/src/main/kotlin/com/customDB/server/HealthRegistry.kt
package com.customDB.server

import com.customDB.api.ClusterCfg
import com.customDB.api.NodeRef
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class NodeHealth(val lastOkMillis: Long, val lastCode: Int)

/** Слушатель изменений кластера + фоновый health-пинг. */
object HealthRegistry : ClusterListener {

    private val health = ConcurrentHashMap<Pair<String, Int>, NodeHealth>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var counterIndex = 0

    /** Старт фонового опроса. */
    fun startPoll(intervalSeconds: Long = 60) {
        scheduler.scheduleAtFixedRate({ pollAll() }, 0, intervalSeconds, TimeUnit.SECONDS)
    }

    fun stopPoll() = scheduler.shutdownNow()

    /** Разовый пинг SQL-узла из роутера (обновляет кэш). */
    fun pingNow(n: NodeRef, path: String = "/health", timeoutMs: Int = 1500): Boolean {
        val code = httpGet(n.host, n.port, path, timeoutMs)
        val key = n.host to n.port
        if (code in 200..299) {
            health[key] = NodeHealth(System.currentTimeMillis(), code)
        } else {
            val old = health[key] ?: NodeHealth(0L, -1)
            health[key] = old.copy(lastCode = code)
        }
        return code in 200..299
    }

    /** Мягкая проверка «живости» по последнему успешному пингу. */
    fun isAlive(n: NodeRef, staleMs: Long = 90_000): Boolean {
        val nh = health[n.host to n.port] ?: return false
        return System.currentTimeMillis() - nh.lastOkMillis < staleMs
    }

    /** Реакция на обновление cluster.json — печатаем сводку SQL/REPL. */
    override fun invoke(prev: ClusterCfg?, cur: ClusterCfg) {
        // SQL-порты
        val sqlChecks = buildList {
            cur.shards.forEach { sh ->
                add(sh.master.host to sh.master.port)
                sh.replicas.forEach { add(it.host to it.port) }
            }
        }.map { (h, p) -> "SQL  $h:$p = ${httpGet(h, p, "/health")}" }

        // REPL-порты (sql+1000) -> /repl/heartbeat
        val replChecks = buildList {
            cur.shards.forEach { sh ->
                // при желании можно добавить и мастерский repl-порт: (sh.master.port + 1000)
                sh.replicas.forEach { add(it.host to (it.port + 1000)) }
            }
        }.map { (h, p) -> "REPL $h:$p = ${httpGet(h, p, "/repl/heartbeat")}" }

        println("[HEALTH] summary:")
        (sqlChecks + replChecks).forEach { println("[HEALTH] $it") }
    }

    /** Фоновая проверка всех SQL-узлов; REPL — только логируем. */
    private fun pollAll() {
        val cfg = ClusterBus.current().cfg
        counterIndex++;
        // SQL
        cfg.shards.flatMap { listOf(it.master) + it.replicas }.forEach { node ->
            val code = httpGet(node.host, node.port, "/health", 1000)
            val key = node.host to node.port
            if (code in 200..299) {
                health[key] = NodeHealth(System.currentTimeMillis(), code)
            } else {
                val old = health[key] ?: NodeHealth(0L, -1)
                health[key] = old.copy(lastCode = code)
            }
            println("[HEALTH] summary ${counterIndex}: ${node.port} answer is ${health[key]?.lastCode}")
        }
        // REPL (лог)
        cfg.shards.forEach { sh ->
            sh.replicas.forEach { r ->
                val rp = r.port + 1000
                val code = httpGet(r.host, rp, "/repl/heartbeat", 1000)
                println("[HEALTH] repl   : $rp answer is $code")
            }
        }
    }

    /** Неблокирующий GET с таймаутами, возвращает HTTP-код или -1. */
    private fun httpGet(host: String, port: Int, path: String, timeoutMs: Int = 1500): Int =
        try {
            val url = URL("http://$host:$port$path")
            (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }.let { conn ->
                conn.inputStream.use { /* drain */ }
                conn.responseCode
            }
        } catch (_: Throwable) {
            -1
        }
}
