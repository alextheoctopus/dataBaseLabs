package com.customDB

import com.customDB.api.*
import com.customDB.api.Row
import com.customDB.node.repl.PrimaryReplicatorHttp
import com.customDB.node.repl.ReplicaApplierHttp
import com.customDB.server.ClusterState
import com.customDB.server.RouterHttpServer
import com.customDB.server.ShardLocator
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.*
import org.apache.spark.sql.RowFactory
import java.io.File
import kotlin.system.measureTimeMillis
import kotlinx.serialization.json.Json

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

    server.createContext("/health") { ex ->
        val body = "OK"
        ex.sendResponseHeaders(200, body.toByteArray().size.toLong())
        ex.responseBody.use { it.write(body.toByteArray()) }
    }

    server.createContext("/execute") { ex ->
        if (ex.requestMethod != "POST") { ex.sendResponseHeaders(405, -1); return@createContext }
        val query = ex.requestBody.readAllBytes().decodeToString()
        try {
            val res = sql.execute(query)
            val body = (res?.toString() ?: "null")
            ex.sendResponseHeaders(200, body.toByteArray().size.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        } catch (t: Throwable) {
            val body = "ERROR: ${t.message}"
            ex.sendResponseHeaders(500, body.toByteArray().size.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        }
    }

    server.createContext("/query") { ex ->
        if (ex.requestMethod != "POST") { ex.sendResponseHeaders(405, -1); return@createContext }
        val query = ex.requestBody.readAllBytes().decodeToString()
        try {
            val res = sql.execute(query)
            val body = when (res) {
                null -> "[]"
                is List<*> -> res.joinToString(prefix = "[", postfix = "]") { it.toString() }
                else -> res.toString()
            }
            ex.sendResponseHeaders(200, body.toByteArray().size.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        } catch (t: Throwable) {
            val body = "ERROR: ${t.message}"
            ex.sendResponseHeaders(500, body.toByteArray().size.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        }
    }

    server.executor = java.util.concurrent.Executors.newCachedThreadPool()
    server.start()
    println("Fallback HTTP server started on :$port  (endpoints: /health, /execute, /query)")
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

        val cluster = ClusterState.load(clusterFile)
        val locator = ShardLocator(cluster, shardKey)
        val router  = RouterHttpServer(cluster, locator)
        router.start(routerPort)
        println("Router started on :$routerPort, cluster=${clusterFile.absolutePath}, shardKey=$shardKey")
        return
    }

    // режим узла (leader/replica)
    val role    = args["role"] ?: "leader"           // "leader" | "replica"
    val shardId = args["shardId"] ?: "s0"
    val replPort = (args["replPort"] ?: "9001").toInt()
    val replicas = parseReplicas(args["replicas"])   // формат: host:replPort,host:replPort

    val storage = LocalStorageEngine(basePath)

    when (role.lowercase()) {
        "leader" -> {
            val replicator = PrimaryReplicatorHttp(shardId, replicas, Json { encodeDefaults = true })
            val sql = SqlEngine(storage, shardId, replicator)
            startSqlHttpServer(storage, sql, port)

            // лидеру свой HTTP для репликации не обязателен, но можно добавить /repl/heartbeat:
            val hb = ReplicaApplierHttp(storage) // используем только для heartbeat значения applied
            hb.startHttp(replPort)
            println("Leader shard=$shardId started on :$port, repl :$replPort, replicas=$replicas")
        }
        "replica" -> {
            val sql = SqlEngine(storage, shardId, null) // на реплике публикации нет
            startSqlHttpServer(storage, sql, port)

            val replSrv = ReplicaApplierHttp(storage)
            replSrv.startHttp(replPort)
            println("Replica shard=$shardId started on :$port, repl :$replPort")
        }
        else -> error("Unknown --role=$role (use leader|replica or --router)")
    }

    convertTableToParquetOrc("output")
}

private fun convertTableToParquetOrc(outputDir: String) {
    val spark = SparkSession.builder()
        .appName("CustomDB to Parquet/ORC")
        .master("local[*]")
        .config("spark.driver.bindAddress", "127.0.0.1")
        .config("spark.driver.host", "127.0.0.1")
        .getOrCreate()

    val allRecords: List<RecordFormat.RecordLineLocal> = (1..1_000_000).map { i ->
        RecordFormat.RecordLineLocal(
            id = i.toLong(),
            tombstone = false,
            payload = Row(
                mutableMapOf(
                    "id" to FieldType.LONG(i.toLong()),
                    "name" to FieldType.STRING("User$i"),
                    "age" to FieldType.LONG((18 + i % 50).toLong())
                )
            )
        )
    }

    println("DEBUG: allRecords loaded = ${allRecords.size}")

    // Преобразуем в Spark Rows
    val sparkRows = allRecords.map { rec ->
        RowFactory.create(
            rec.id as java.lang.Long, // RowFactory требует java.lang.Long
            rec.payload.values["name"]?.toString(),
            (rec.payload.values["age"] as? FieldType.LONG)?.v as java.lang.Long?
        )
    }

    val schema = StructType(
        arrayOf(
            StructField("id", DataTypes.LongType, false, org.apache.spark.sql.types.Metadata.empty()),
            StructField("name", DataTypes.StringType, true, org.apache.spark.sql.types.Metadata.empty()),
            StructField("age", DataTypes.LongType, true, Metadata.empty())
        )
    )

    val df = spark.createDataFrame(sparkRows, schema)
    val filteredDF = df.filter("age > 20")

    // Сохраняем Parquet и ORC
    val parquetDir = "$outputDir/parquet"
    val orcDir = "$outputDir/orc"
    filteredDF.write().mode("overwrite").parquet(parquetDir)
    filteredDF.write().mode("overwrite").orc(orcDir)

    // Замер времени чтения
    val parquetReadTime = measureTimeMillis { spark.read().parquet(parquetDir).show() }
    val orcReadTime = measureTimeMillis { spark.read().orc(orcDir).show() }

    // Размер файлов
    val parquetSize = File(parquetDir).walkTopDown().sumOf { if (it.isFile) it.length() else 0L }
    val orcSize = File(orcDir).walkTopDown().sumOf { if (it.isFile) it.length() else 0L }

    println("Parquet size = $parquetSize bytes, read time = $parquetReadTime ms")
    println("ORC size = $orcSize bytes, read time = $orcReadTime ms")

    spark.stop()
}
