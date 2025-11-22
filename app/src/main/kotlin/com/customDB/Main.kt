

package com.customDB

import com.customDB.api.*
import com.customDB.node.repl.PrimaryReplicatorHttp
import com.customDB.node.repl.ReplicaApplierHttp
import com.customDB.server.ClusterState
import com.customDB.server.RouterHttpServer
import com.customDB.server.ShardLocator
import kotlinx.serialization.json.Json
import java.io.File
import java.net.InetSocketAddress

/** Примитивный парсер аргументов формата --key=value / --flag */
private fun parseArgs(args: Array<String>): Map<String, String?> =
    args.associate { s ->
        if (s.startsWith("--")) {
            val eq = s.indexOf('=')
            if (eq > 2) s.substring(2, eq) to s.substring(eq + 1)
            else s.substring(2) to "true"
        } else s to null
    }
private fun startFallbackServer(sql: SqlEngine, port: Int) {
    val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(port), 0)

    fun respond(ex: com.sun.net.httpserver.HttpExchange, code: Int, body: String, contentType: String = "application/json; charset=utf-8") {
        ex.responseHeaders.add("Content-Type", contentType)
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    fun ok(payload: Any?): String = when (payload) {
        null -> """{"status":"OK"}"""
        is Number, is Boolean -> payload.toString()
        is String -> """{"status":"OK","detail":${payload.trim().let { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }}}"""
        is Iterable<*> -> payload.joinToString(prefix = "[", postfix = "]") { it?.toString()?.let { s -> "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\"" } ?: "null" }
        is Map<*, *> -> payload.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            "\"${k.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\":${ok(v)}"
        }
        else -> "\"${payload.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

    fun err(message: String?): String = """{"error":${("\"" + (message ?: "Unexpected error")).replace("\\", "\\\\").replace("\"", "\\\"") + "\""}}"""

    server.createContext("/health") { ex ->
        // простой текст, чтобы health-чекеры не спотыкались о JSON
        val body = "OK"
        ex.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    fun handleSql(ex: com.sun.net.httpserver.HttpExchange) {
        val method = ex.requestMethod.uppercase()
        if (method != "POST") {
            respond(ex, 405, err("Method Not Allowed"))
            return
        }
        val query = ex.requestBody.readAllBytes().toString(Charsets.UTF_8).trim()
        if (query.isEmpty()) {
            respond(ex, 400, err("Empty body"))
            return
        }
        try {
            // лёгкое логирование запросов
            println("[fallback:${ex.requestURI.path}] ${query.take(200)}")
            val res = sql.execute(query)
            respond(ex, 200, ok(res))
        } catch (t: Throwable) {
            respond(ex, 500, err(t.message))
        }
    }

    server.createContext("/execute", ::handleSql)
    server.createContext("/query",   ::handleSql)

    // пул потоков; для нагрузки лучше фиксированный thread-pool
    server.executor = java.util.concurrent.Executors.newCachedThreadPool()
    server.start()
    println("Fallback HTTP server started on :$port (endpoints: /health, /execute, /query)")
}


private fun parseReplicas(csv: String?): List<NodeRef> =
    csv?.split(",")?.filter { it.isNotBlank() }?.map {
        val (h, p) = it.trim().split(":")
        NodeRef(h, p.toInt())
    } ?: emptyList()

/** Универсальный запуск SqlHttpServer без знания точной сигнатуры */
private fun startSqlHttpServer(engine: LocalStorageEngine, sql: SqlEngine, port: Int) {
    // 1) Пробуем найти и запустить ваш com.customDB.server.SqlHttpServer
    try {
        val cls = Class.forName("com.customDB.server.SqlHttpServer")

        val inst: Any? =
            // ctor(SqlEngine)
            cls.constructors.firstOrNull { c ->
                val t = c.parameterTypes
                t.size == 1 && t[0].name == SqlEngine::class.java.name
            }?.newInstance(sql)
            // ctor(LocalStorageEngine)
                ?: cls.constructors.firstOrNull { c ->
                    val t = c.parameterTypes
                    t.size == 1 && t[0].name == LocalStorageEngine::class.java.name
                }?.newInstance(engine)
                // ctor(port, SqlEngine)
                ?: cls.constructors.firstOrNull { c ->
                    val t = c.parameterTypes
                    t.size == 2 && t[0] == Int::class.java && t[1].name == SqlEngine::class.java.name
                }?.newInstance(port, sql)
                // ctor(port, LocalStorageEngine)
                ?: cls.constructors.firstOrNull { c ->
                    val t = c.parameterTypes
                    t.size == 2 && t[0] == Int::class.java && t[1].name == LocalStorageEngine::class.java.name
                }?.newInstance(port, engine)
                // пустой ctor()
                ?: cls.constructors.firstOrNull { it.parameterTypes.isEmpty() }?.newInstance()

        if (inst != null) {
            // start(port)
            cls.methods.firstOrNull { it.name == "start" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.java }
                ?.let { it.invoke(inst, port); return }

            // start(port, SqlEngine)
            cls.methods.firstOrNull { it.name == "start" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == Int::class.java && it.parameterTypes[1].name == SqlEngine::class.java.name }
                ?.let { it.invoke(inst, port, sql); return }

            // start(port, LocalStorageEngine)
            cls.methods.firstOrNull { it.name == "start" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == Int::class.java && it.parameterTypes[1].name == LocalStorageEngine::class.java.name }
                ?.let { it.invoke(inst, port, engine); return }

            // setEngine(sql) + start(port)
            cls.methods.firstOrNull { it.name == "setEngine" && it.parameterTypes.size == 1 && it.parameterTypes[0].name == SqlEngine::class.java.name }
                ?.also { it.invoke(inst, sql) }
            cls.methods.firstOrNull { it.name == "start" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.java }
                ?.let { it.invoke(inst, port); return }
        }
        // если сюда дошли — не получилось; падаем в fallback
        println("SqlHttpServer not found/unsupported signature, starting fallback HTTP server on :$port")
    } catch (_: ClassNotFoundException) {
        println("SqlHttpServer class not found, starting fallback HTTP server on :$port")
    }

    // 2) Fallback: минимальный HTTP-сервер
    startFallbackServer(sql, port)
}


fun main(vararg raw: String) {
    val args = parseArgs(raw as Array<String>)

    // общие параметры
    val basePath = File(args["basePath"] ?: "src/LocalDB")
    val port     = (args["port"] ?: "8001").toInt()

    // режим router?
    val isRouter = args["router"] == "true" || args["mode"] == "router"
    if (isRouter) {
        val clusterFile = File(args["cluster"] ?: "cluster/cluster.json")
        val routerPort  = (args["routerPort"] ?: "8080").toInt()
        val shardKey    = args["shardKey"] ?: "id"
        //Создание объектов
/*Загружаем конфиг кластера (из clusterFile, обычно JSON): строится объект ClusterState с
списком шардов (их id, master и реплики), разбиением хэш-слотов между шардами, методами вроде masterOf(shardId), bestReplicaOrmaster(shardId), shardBySlot(slot).*/
        val cluster = ClusterState.load(clusterFile)
        /*Создаём локатор шарда. Он:
парсит входящий SQL (JSqlParser),
вытаскивает значение ключа шардирования (shardKey, по умолчанию id) из WHERE id = ...,
хэширует это значение и по таблице слотов из cluster определяет какой шард должен обслужить запрос.*/
        val locator = ShardLocator(cluster, shardKey)
/*Создаём HTTP-роутер. Он будет:
принимать внешние запросы (/execute и /query), через locator вычислять целевой шард,
через cluster выбирать лидера (для записи) или лучшую реплику/лидера (для чтения),
форвардить запрос на соответствующий узел кластера.*/
        val router  = RouterHttpServer(cluster, locator)
        router.start(routerPort)
        println("Router started on :$routerPort, cluster=${clusterFile.absolutePath}, shardKey=$shardKey")
        return
    }

    // режим узла (master/replica)
    val role    = args["role"] ?: "master"           // "master" | "replica"
    val shardId = args["shardId"] ?: "s0"
    val replPort = (args["replPort"] ?: "9001").toInt()
    val replicas = parseReplicas(args["replicas"])   // формат: host:replPort,host:replPort

    val storage = LocalStorageEngine(basePath)

    when (role.lowercase()) {
        "master" -> {
            val replicator = PrimaryReplicatorHttp(shardId, replicas, Json { encodeDefaults = true })
            val sql = SqlEngine(storage, shardId, replicator)
            startSqlHttpServer(storage, sql, port)

            // лидеру свой HTTP для репликации не обязателен, но можно добавить /repl/heartbeat:
            val hb = ReplicaApplierHttp(storage) // используем только для heartbeat значения applied
            hb.startHttp(replPort)
            println("master shard=$shardId started on :$port, repl :$replPort, replicas=$replicas")
        }
        "replica" -> {
            val sql = SqlEngine(storage, shardId, null) // на реплике публикации нет
            startSqlHttpServer(storage, sql, port)

            val replSrv = ReplicaApplierHttp(storage)
            replSrv.startHttp(replPort)
            println("Replica shard=$shardId started on :$port, repl :$replPort")
        }
        else -> error("Unknown --role=$role (use master|replica or --router)")
    }
}

