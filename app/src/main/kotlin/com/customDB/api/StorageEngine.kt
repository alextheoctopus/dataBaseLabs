package com.customDB.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

//import arrow.core.Either

/** Движок хранения таблиц в локальном каталоге. */
class LocalStorageEngine : AutoCloseable {
    /** Базовая директория для файлов таблиц/индексов/метаданных. */
    val basePath: File = File("src", "LocalDB")


    /* Открыть существующую таблицу по имени.*/
    //  открывает meta file и преобразует из структуры json таблицу в оперативную память
    /** Создать таблицу с заданной схемой (ошибка, если существует). */
    fun createTable(table: TableSchema): Table {

        val pathTableData = File(basePath, "${table.name}.tbl");//Строки таблицы
        val pathTableMeta = File(basePath, "${table.name}.meta");//Поля таблицы

        if (pathTableData.exists()) {
            println(TinyDbException.TableAlreadyExists(pathTableData.name));
        } else {
            pathTableData.createNewFile();
            pathTableMeta.createNewFile();
            val metaData = Json.encodeToString(table)
            pathTableMeta.appendText("\n" + metaData)
        }
        return openTable(table.name)// В любом случае открываем и возвращаем таблицу
    }

    override fun close() {
    }

    //Возвращает схему таблицы. Название полей и их типы
    private fun openTable(name: String): Table {//Была попытка использовать Either, неудачная...
        val fileLink = File("src/LocalDB/${name}.meta")

        if (fileLink.exists()) {
            //чтение данных о столбцах таблицы из мета файла
            val structureData: String = fileLink.readText();

            // Мета данные таблицы в виде TableSchema
            //TODO: перенести в метод декодирования
            val tableSchemaFromFile = Json.decodeFromString(TableSchema.serializer(), structureData)
            return LocalTable(tableSchemaFromFile.name, tableSchemaFromFile)
        } else {
            throw Error(TinyDbException.TableNotFound(name))
        }
    }
    fun generateRowId(tableName:String):FieldType.LONG{
        //Сгенерировать актуальный уникальный айдишник
        val fileLink = File("src/LocalDB/${tableName}.tbl")
        val id:FieldType.LONG;

        if(fileLink.exists()){
            val readResult=fileLink.readLines().lastOrNull()
            //ATTENTION: Вот тут возвращается последняя строка или null. Тип -> (string?)
            //А мы будем записывать в формате RecordFile, поэтому я пока не знаю как их подружить,
            // возможно с функцией декодером, хотя у нас там будет ByteArray, непонятное...
            if(readResult!=null){
                val lastRow:RecordFormat.RecordFile=Json.decodeFromString(RecordFormat.RecordFile.serializer(),readResult)
                id= FieldType.LONG(lastRow.id+1)
            }else {
                id= FieldType.LONG(0)
            }

        }else{
            throw Error(TinyDbException.TableNotFound(tableName))
        }
        return id
    }
//
//    /** Удалить таблицу (все связанные файлы). Возвращает true, если что-то удалено. */
//    fun dropTable(name: String): Boolean
//
//    /** Регистрация индексов (опционально — можно сделать фабрику индексов). */
//    fun listTables(): List<String>
//
}
