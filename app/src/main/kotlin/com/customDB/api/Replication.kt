//Интерфейсы репликации
package com.customDB.api

import kotlinx.serialization.Serializable

@Serializable
sealed class RepOp {
    @Serializable data class Insert(val table: String, val row: Row, val id: Long? = null): RepOp()
    @Serializable data class Upsert(val table: String, val id: Long, val newValues: Row): RepOp()
    @Serializable data class Delete(val table: String, val fields: Map<String, FieldType>): RepOp()
    @Serializable data class CreateTable(val schema: TableSchema): RepOp()
    @Serializable data class DropTable(val table: String): RepOp()
}

@Serializable data class RepBatch(
    val shardId: String,
    val ops: List<RepOp>
)

interface PrimaryReplicator { suspend fun publish(batch: RepBatch) }
interface ReplicaApplier     { suspend fun apply(batch: RepBatch); fun appliedCount(): Long }
