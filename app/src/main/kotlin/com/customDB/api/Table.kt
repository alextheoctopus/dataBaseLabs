package com.customDB.api

import kotlinx.serialization.json.Json
import java.io.File

/** Контракт таблицы. */
interface Table {
    val name: String
    val schema: TableSchema
    val record: RecordFormat

    /** Вставить строку. Возвращаем СТРОКУ, записанную в .tbl */
    fun insert(values: Row, id: FieldType.LONG? = null, tombstone: FieldType.BOOL? = null)/*: String*/

    fun get(fields: Map<String, FieldType>): List<RecordFormat.RecordLineLocal>?

    fun upsert(
        id: RowId,
        newValues: Row,
    ): Boolean

    fun delete(fields: Map<String, FieldType>): Boolean

//    fun scan(
//        predicate: Predicate = Q.any(),
//        sort: Sort = Sort(),
//        limit: Int = Int.MAX_VALUE,
//        offset: Int = 0,
//    ): Cursor<Row>
//
//    fun countApprox(): Long

    fun compact(): Boolean
}

class LocalTable(
    override val name: String,
    override val schema: TableSchema,
    baseDir: File,
) : Table {
    private val json = Json { encodeDefaults = true }


    private val dataFile = File(baseDir, "$name.tbl")
    override val record: RecordFormat = RecordFormat(dataFile)


    /** Простой генератор id: 1 + число непустых строк в .tbl */
    private fun nextRowId(): FieldType.LONG {
        if (!dataFile.exists()) return FieldType.LONG(1)
        val lines = dataFile.useLines { seq -> seq.count { it.isNotBlank() } }
        return FieldType.LONG(lines + 1L)
    }

    private fun getRecord(): List<RecordFormat.RecordLineLocal> {
        var result: List<RecordFormat.RecordLineLocal> = record.readAll(schema)
//            .map { rec ->
//                RecordFormat.RecordLineLocal(
//                    tombstone = rec.tombstone,
//                    id = rec.id,
//                    payload = json.decodeFromString(Row.serializer(), rec.payload)
//                )
//            }
            .filter { !it.tombstone }//только живые
        return result;
    }

    /**Очищение от мертвых записей*/
    override fun compact(): Boolean {
        var result: Boolean = false;
        val recordJson = getRecord();
        val updatedJson: List<RecordFormat.RecordLineLocal> = recordJson.filter { !it.tombstone }
        dataFile.writeText("")
        for (row in updatedJson) {
            insert(row.payload, FieldType.LONG(row.id), FieldType.BOOL(row.tombstone))
        }
        return result
    }

    /** Вставить строку и вернуть СТРОКУ, которая записана в .tbl */
    override fun insert(values: Row, id: FieldType.LONG?, tombstone: FieldType.BOOL?)/*: String*/ {
        // если id не передан → генерим новый
        val rowId: Long = id?.v ?: nextRowId().v

        // если tombstone не передан → считаем, что запись живая
        val tombstoneFlag = tombstone?.v ?: false

        println("ROWID  " + rowId);
        return record.append(
            RecordFormat.RecordLineLocal(
                tombstoneFlag,
                rowId,
                values//должен всегда содержать все столбцы? сделать проверку
            ),
            schema
        )
    }


    //Возвращает живые строки по заданным полям
    override fun get(fields: Map<String, FieldType>): List<RecordFormat.RecordLineLocal>? {

        val record = getRecord()
        if (fields.isEmpty()) return record
        val foundData = mutableListOf<RecordFormat.RecordLineLocal>()

        for (rec in record) {
            if (!rec.tombstone) {//если запись мертвая, то пропустить

                // проверяем, что все поля совпадают и живые
                val matches = fields.all { (key, value) ->
                    rec.payload.values[key] == value
                }

                if (matches) {
                    foundData.add(rec)
                }
            }
        }


        return if (foundData.isNotEmpty()) foundData else null
    }


    override fun upsert(
        id: RowId,
        newValues: Row,
    ): Boolean {
        //сканировать опять весь файл и привести к json
        val recordJson = getRecord()
        //найти строку по id через get и если такой нет, то создать через инсерт,
        val line = recordJson.find { lineId -> lineId.id == id.v }
        var result: Boolean;
        if (line != null) {
            for ((key, value) in newValues.values) {
                line.payload.values[key] = value
                // а если есть то изменить поля
            }
            //пройтись по json и перезаписать строку
            val updatedJson: List<RecordFormat.RecordLineLocal> = recordJson.map { row ->
                if (row.id == line.id) {
                    RecordFormat.RecordLineLocal(
                        tombstone = line.tombstone,
                        id = line.id,
                        payload = Row(line.payload.values)
                    )
                } else {
                    row
                }
            }
            dataFile.writeText("")
            for (row in updatedJson) {
                insert(row.payload, FieldType.LONG(row.id), FieldType.BOOL(row.tombstone))
            }
            result = true
        } else {
            insert(newValues)
            result = true

        }

        return result
    }

    override fun delete(fields: Map<String, FieldType>): Boolean {
        val all = record.readAll(schema)
//            .map { rec ->
//            RecordFormat.RecordLineLocal(
//                tombstone = rec.tombstone,
//                id = rec.id,
//                payload = json.decodeFromString(Row.serializer(), rec.payload)
//            )
//        }

        var changed = false

        fun matches(rec: RecordFormat.RecordLineLocal): Boolean {
            return fields.all { (k, v) ->
                if (k == "id") {
                    (v is FieldType.LONG) && (v.v == rec.id)
                } else {
                    rec.payload.values[k] == v
                }
            }
        }

        val updated = all.map { rec ->
            if (!rec.tombstone && matches(rec)) {
                changed = true
                rec.copy(tombstone = true)
            } else rec
        }

        if (changed) {
            dataFile.writeText("")
            updated.forEach { line ->
                insert(line.payload, FieldType.LONG(line.id), FieldType.BOOL(line.tombstone))
            }
        }
        return changed
    }


}
