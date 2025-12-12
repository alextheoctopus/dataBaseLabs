package com.customDB

import com.customDB.api.LocalStorageEngine
import com.customDB.api.NodeRef
import com.customDB.api.SqlEngine
import com.customDB.node.repl.ReplicationHttpServer
import com.customDB.node.repl.PrimaryReplicatorHttp
import com.customDB.server.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private fun resolveClusterFile(): File {
    // 1) явный путь через JVM аргумент: -Dcluster.file=...
    System.getProperty("cluster.file")?.let { p ->
        val f = File(p)
        if (f.isFile) return f
    }
    // 2) набор типичных путей относительно корня проекта и модуля app
    val candidates = listOf(
        Paths.get("app", "cluster", "cluster.json"),
        Paths.get("cluster", "cluster.json"),
        Paths.get("app\\cluster\\cluster.json"),
        Paths.get("src", "main", "resources", "cluster.json") // на всякий
    )
    val found: Path? = candidates.firstOrNull { Files.isRegularFile(it) }
    if (found != null) {
        val f = found.toFile()
        println("[Main] using cluster file: ${f.absolutePath}")
        return f
    }
    error(
        "cluster.json not found. Checked:\n" +
                candidates.joinToString("\n") { " - ${it.toAbsolutePath()}" } +
                "\nOr run with -Dcluster.file=C:/path/to/cluster.json"
    )
}

//TODO:
//дублирую данные в аргументах , могу неправильно поднять мастера и реплику,
//вынести кластерстейт чтобы о нем все знали и все были на него подписаны,
//если я захотела добавить реплику оно все подхватывалось
//роутеру еще нужно знать что все ноды живы, организовать разговор между роутером и нодами,
//сделать общение раз в минуту(вынести параметр частоты опроса).

fun main() {
    val clusterFile = resolveClusterFile()

    // 1) стартуем watcher
    ClusterBus.initAndWatch(
        clusterFile = clusterFile,
        onChange = HealthRegistry
    )
    //Проверка нод каждую минуту
    HealthRegistry.startPoll(60)
    // 2) оркестратор поднимает всё из текущего конфига
    val orchestrator = NodeOrchestrator()
    orchestrator.startAll(ClusterBus.current().cfg)

    // 3) подписываемся на изменения
    ClusterBus.addListener { prev, cur ->
        orchestrator.applyDiff(prev, cur)
    }

    // 4) роутер
    val locator = ShardLocator() // если он использует только sql → id → slot, ему не нужен ClusterState в конструкторе
    RouterHttpServer(locator).start(8080)

    println("Router listening on :8080")
    Thread.currentThread().join()
}


// Простой тест производительности (закомментирован)
//fun main() {
//    val engine = LocalStorageEngine()
//    val sql = SqlEngine(engine)
//
//    sql.execute("CREATE TABLE users (id INT, name STRING, age INT)")
//
//    val n = 10000
//    val startInsert = System.nanoTime()
//    for (i in 1..n) {
//        sql.execute("INSERT INTO users (id, name, age) VALUES ($i, 'user$i', ${i % 50})")
//    }
//    val insertMs = (System.nanoTime() - startInsert) / 1_000_000
//
//    val startSelect = System.nanoTime()
//    sql.execute("SELECT * FROM users WHERE id = ${n / 2}")
//    val selectMs = (System.nanoTime() - startSelect) / 1_000_000
//    println("customDB: inserted $n rows in ${insertMs} ms")
//    println("customDB: point select in ${selectMs} ms")
//}
