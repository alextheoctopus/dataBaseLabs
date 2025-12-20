# Инструкция по запуску и демонстрации GZIP реализации

## Способ 1: Запуск тестов (рекомендуется)

### Запустить все тесты сжатия:
```bash
./gradlew test --tests "*Compression*"
```

### Запустить конкретный тест GZIP совместимости:
```bash
./gradlew test --tests "com.customDB.compression.GzipFormatTest"
```

### Запустить тест совместимости с gunzip:
```bash
./gradlew test --tests "com.customDB.compression.GzipFormatTest.testGzipCompatibility"
```

## Способ 2: Запуск демо скрипта

### Вариант A: Через Gradle
```bash
./gradlew run -PmainClass=demo_gzipKt
```

### Вариант B: Через Kotlin компилятор
```bash
kotlinc demo_gzip.kt -include-runtime -d demo.jar
java -jar demo.jar
```

Это создаст файл `demo_test.gz` в корне проекта, который можно проверить через gunzip.

## Способ 3: Ручная проверка через gunzip

### Шаг 1: Создать GZIP файл программно

Создайте файл `CreateGzipFile.kt`:
```kotlin
import com.customDB.compression.GzipFormat
import java.io.File

fun main() {
    val text = """
        This is a test file for demonstrating GZIP compression.
        It contains repeated text: "Hello World" "Hello World" "Hello World"
        More repetition: ABC ABC ABC ABC ABC
    """.trimIndent()
    
    val compressed = GzipFormat.compress(text.toByteArray())
    val gzipFile = File("test_output.gz")
    gzipFile.writeBytes(compressed)
    
    println("GZIP file created: ${gzipFile.absolutePath}")
    println("Original size: ${text.toByteArray().size} bytes")
    println("Compressed size: ${compressed.size} bytes")
}
```

Запустите:
```bash
./gradlew run -PmainClass=CreateGzipFileKt
```

### Шаг 2: Проверить через gunzip
```bash
# Распаковать и вывести содержимое
gunzip -c test_output.gz

# Или через zcat
zcat test_output.gz

# Проверить целостность файла
gunzip -t test_output.gz

# Распаковать в файл
gunzip -k test_output.gz
cat test_output
```

### Шаг 3: Сравнить с оригиналом
```bash
# Создать оригинальный файл
echo 'This is a test file for demonstrating GZIP compression.
It contains repeated text: "Hello World" "Hello World" "Hello World"
More repetition: ABC ABC ABC ABC ABC' > original.txt

# Сжать через нашу программу
# (используйте CreateGzipFile.kt)

# Распаковать через gunzip
gunzip -c test_output.gz > decompressed.txt

# Сравнить
diff original.txt decompressed.txt
# Если файлы идентичны - вывод будет пустым
```

## Способ 4: Интеграция с базой данных

### Запустить тесты интеграции:
```bash
./gradlew test --tests "*DatabaseCompression*"
```

### Пример использования в коде:
```kotlin
import com.customDB.compression.GzipFormat
import java.io.File

// Сжать данные
val data = "Some text with repetitions: ABC ABC ABC".toByteArray()
val compressed = GzipFormat.compress(data)

// Сохранить в файл
File("output.gz").writeBytes(compressed)

// Распаковать
val decompressed = GzipFormat.decompress(compressed)
println(String(decompressed)) // Должно вывести оригинальный текст
```

## Проверка совместимости

### Что должно работать:
1. ✅ `gunzip -c file.gz` - распаковка и вывод
2. ✅ `zcat file.gz` - распаковка и вывод
3. ✅ `gunzip -t file.gz` - проверка целостности
4. ✅ `7z x file.gz` - распаковка через 7z
5. ✅ Java `GZIPInputStream` - распаковка через Java библиотеку

### Пример полной проверки:
```bash
# 1. Создать файл через нашу программу
./gradlew run -PmainClass=demo_gzipKt

# 2. Проверить через gunzip
gunzip -t demo_test.gz && echo "✓ File is valid"

# 3. Распаковать и сравнить
gunzip -c demo_test.gz > /tmp/decompressed.txt
# Сравнить с оригиналом (если есть)

# 4. Проверить размер
ls -lh demo_test.gz
```

## Отладка

Если gunzip не работает:

1. **Проверить магические байты:**
   ```bash
   hexdump -C test_output.gz | head -1
   # Должно начинаться с: 1f 8b 08
   ```

2. **Проверить ошибки gunzip:**
   ```bash
   gunzip -v test_output.gz 2>&1
   ```

3. **Проверить наше распаковывание:**
   ```kotlin
   val decompressed = GzipFormat.decompress(compressed)
   // Если это работает, проблема в формате файла
   ```

## Ожидаемый результат

При успешной работе:
- ✅ Тесты проходят
- ✅ gunzip успешно распаковывает созданные файлы
- ✅ Данные восстанавливаются без потерь
- ✅ CRC32 проверка проходит
- ✅ Размер исходных данных совпадает
