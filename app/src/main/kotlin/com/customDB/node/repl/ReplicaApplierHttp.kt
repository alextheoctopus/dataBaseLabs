package com.customDB.node.repl

import com.customDB.api.*
import kotlinx.serialization.json.Json
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking

class ReplicaApplierHttp(
    private val engine: LocalStorageEngine,
    private val json: Json = Json { encodeDefaults = true }
): ReplicaApplier {

    private val applied = AtomicLong(0)

    override fun appliedCount(): Long = applied.get()

    override suspend fun apply(batch: RepBatch) {
        batch.ops.forEach { op ->
            when (op) {
                is RepOp.CreateTable -> engine.getOrCreateTable(op.schema)
                is RepOp.DropTable   -> {
                    // используем ту же логику, что в SqlEngine.handleDrop
                    val name = op.table
                    val data = java.io.File(engine.basePath, "$name.tbl")
                    val meta = java.io.File(engine.basePath, "$name.meta")
                    data.delete(); meta.delete()
                }
                is RepOp.Insert -> {
                    val table = engine.getOrCreateTableFromMeta(op.table)
                    table.insert(op.row, op.id?.let { FieldType.LONG(it) }, null)
                }
                is RepOp.Upsert -> {
                    val table = engine.getOrCreateTableFromMeta(op.table)
                    table.upsert(FieldType.LONG(op.id), op.newValues)
                }
                is RepOp.Delete -> {
                    val table = engine.getOrCreateTableFromMeta(op.table)
                    table.delete(op.fields)
                }
            }
        }
        applied.addAndGet(batch.ops.size.toLong())
    }

    /** Вешаем HTTP-эндпоинты реплики */
    fun startHttp(port: Int) {
        val srv = HttpServer.create(InetSocketAddress(port), 0)

        srv.createContext("/repl/heartbeat") { ex ->
            val body = """{"applied":${appliedCount()}}"""
            ex.sendResponseHeaders(200, body.length.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        }

        srv.createContext("/repl/push") { ex ->
            if (ex.requestMethod != "POST") { ex.sendResponseHeaders(405, -1); return@createContext }
            val text = ex.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            val batch = json.decodeFromString(RepBatch.serializer(), text)
            // применяем
            kotlinx.coroutines.runBlocking { apply(batch) }
            val body = """{"applied":${appliedCount()}}"""
            ex.sendResponseHeaders(200, body.length.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        }
        srv.start()
    }
}
