# Быстрый старт: создание GZIP файла

## 1. Запустить тест (проверка что все работает)

```bash
cd /Users/evgeniyforbes/IdeaProjects/dataBaseLabs
./gradlew test --tests "com.customDB.compression.GzipFormatTest.testGzipCompatibility"
```

Этот тест:
- ✅ Создает GZIP файл через нашу реализацию
- ✅ Проверяет что gunzip может его распаковать
- ✅ Сравнивает данные до и после

## 2. Создать GZIP файл вручную

### Вариант A: Через Gradle (рекомендуется)

```bash
cd /Users/evgeniyforbes/IdeaProjects/dataBaseLabs
./gradlew run -PmainClass=CreateGzipFileKt
```

Файл будет создан в корне проекта: `test_output.gz`

### Вариант B: Через Kotlin компилятор

```bash
cd /Users/evgeniyforbes/IdeaProjects/dataBaseLabs
kotlinc -cp "app/build/classes/kotlin/main:app/build/libs/*" CreateGzipFile.kt -include-runtime -d create_gzip.jar
java -jar create_gzip.jar
```

## 3. Проверить созданный файл через gunzip

```bash
# Перейти в директорию проекта
cd /Users/evgeniyforbes/IdeaProjects/dataBaseLabs

# Проверить целостность файла
gunzip -t test_output.gz

# Распаковать и показать содержимое
gunzip -c test_output.gz

# Или через zcat
zcat test_output.gz

# Сохранить распакованный файл
gunzip -c test_output.gz > decompressed.txt
```

## 4. Создать файл из своего текста

Создайте файл `MyGzipFile.kt`:

```kotlin
import com.customDB.compression.GzipFormat
import java.io.File

fun main() {
    // Ваш текст
    val text = """
        Ваш текст здесь
        С повторениями: ABC ABC ABC
    """.trimIndent()
    
    // Сжать
    val compressed = GzipFormat.compress(text.toByteArray())
    
    // Сохранить
    val file = File("my_file.gz")
    file.writeBytes(compressed)
    
    println("Файл создан: ${file.absolutePath}")
    println("Размер: ${compressed.size} байт")
}
```

Запустить:
```bash
./gradlew run -PmainClass=MyGzipFileKt
```

## 5. Использовать в коде

```kotlin
import com.customDB.compression.GzipFormat
import java.io.File

// Сжать данные
val data = "Your text here".toByteArray()
val compressed = GzipFormat.compress(data)

// Сохранить в файл
File("output.gz").writeBytes(compressed)

// Распаковать
val decompressed = GzipFormat.decompress(compressed)
println(String(decompressed))
```

## Существующие тесты

### Все тесты сжатия:
```bash
./gradlew test --tests "*Compression*"
```

### Конкретные тесты:
```bash
# Тест базового сжатия
./gradlew test --tests "com.customDB.compression.GzipFormatTest.testBasicCompression"

# Тест совместимости с gunzip
./gradlew test --tests "com.customDB.compression.GzipFormatTest.testGzipCompatibility"

# Тест с повторяющимися данными
./gradlew test --tests "com.customDB.compression.GzipFormatTest.testRepeatedData"
```

## Где находятся файлы

- **Тесты:** `app/src/test/kotlin/com/customDB/compression/GzipFormatTest.kt`
- **Реализация:** `app/src/main/kotlin/com/customDB/compression/GzipFormat.kt`
- **Скрипт создания:** `CreateGzipFile.kt` (в корне проекта)

## Ожидаемый результат

После запуска `CreateGzipFile.kt`:
- ✅ Файл `test_output.gz` создан в корне проекта
- ✅ `gunzip -t test_output.gz` → "test_output.gz: OK"
- ✅ `gunzip -c test_output.gz` → выводит оригинальный текст
- ✅ Данные восстанавливаются без потерь
