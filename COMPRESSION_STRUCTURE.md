# Структура реализации сжатия

## Где что находится

### ✅ Самостоятельная реализация алгоритма сжатия

**Файл:** `app/src/main/kotlin/com/customDB/compression/LZ77.kt`

**Что делает:**
- Это **наша собственная реализация** алгоритма LZ77
- Ищет повторяющиеся последовательности в данных
- Заменяет их ссылками (offset, length)
- Работает полностью самостоятельно, без использования готовых библиотек

**Как работает:**
1. Проходит по данным байт за байтом
2. Для каждой позиции ищет самое длинное совпадение в предыдущих данных (окно 32KB)
3. Если находит совпадение ≥ 3 байт → создает ссылку (Reference)
4. Если не находит → записывает байт как есть (Literal)
5. Результат: список токенов (Literal или Reference)

**Пример:**
```
Входные данные: "ABCABCABC"
LZ77 результат:
  - Token.Literal('A')
  - Token.Literal('B') 
  - Token.Literal('C')
  - Token.Reference(offset=3, length=6)  // ссылка на "ABCABC"
```

---

### ✅ Самостоятельная реализация формата хранения

**Файл:** `app/src/main/kotlin/com/customDB/compression/LZ77Encoder.kt`

**Что делает:**
- Это **наш собственный бинарный формат** для сериализации LZ77 токенов
- Кодирует токены LZ77 в бинарный формат для хранения
- Работает полностью самостоятельно, без использования готовых библиотек

**Формат:**
- 4 байта: размер исходных данных (little-endian)
- Для каждого токена:
  - Literal: `0x00` + 1 байт (значение байта)
  - Reference: `0x01` + 2 байта offset (little-endian) + 2 байта length (little-endian)

**Пример:**
```
Токены: [Literal(65), Literal(66), Literal(67), Reference(3, 6)]
Бинарный формат:
  [04 00 00 00]  // размер = 9 (little-endian)
  [00 41]        // Literal('A')
  [00 42]        // Literal('B')
  [00 43]        // Literal('C')
  [01 03 00 06 00]  // Reference(offset=3, length=6)
```

---

### ✅ Главный интерфейс для сжатия

**Файл:** `app/src/main/kotlin/com/customDB/compression/CustomCompressionFormat.kt`

**Что делает:**
- Объединяет LZ77 и LZ77Encoder в единый интерфейс
- `compress(data: ByteArray): ByteArray` - сжимает данные
- `decompress(compressedData: ByteArray): ByteArray` - распаковывает данные
- **Используется в базе данных** для сжатия payload

**Как работает:**
```
compress:
  Данные → LZ77.compress() → токены → LZ77Encoder.encode() → бинарный формат

decompress:
  Бинарный формат → LZ77Encoder.decode() → токены → LZ77.decompress() → данные
```

---

### ✅ Интеграция в базу данных

**Файл:** `app/src/main/kotlin/com/customDB/api/Codec.kt`

**Что делает:**
- Использует `CustomCompressionFormat` для сжатия payload при INSERT
- Автоматически распаковывает при SELECT
- Метаданные (id, tombstone, compressed флаг) остаются в JSON

**Как работает:**
1. **INSERT:** 
   - Row (объект) → JSON сериализация → payload (JSON строка)
   - Если payload > 50 байт → `CustomCompressionFormat.compress()`
     - → LZ77.compress() → токены
     - → LZ77Encoder.encode() → бинарный формат
   - Сжатые данные → Base64 → JSON поле `"payload"`
   - Флаг `"compressed": true` устанавливается
   - RecordLine (JSON) → запись в файл .tbl

2. **SELECT:**
   - Читает JSON строку RecordLine из файла .tbl
   - Если `"compressed": true` → Base64.decode() → `CustomCompressionFormat.decompress()`
     - → LZ77Encoder.decode() → токены
     - → LZ77.decompress() → исходный payload (JSON строка)
   - JSON десериализация → Row (объект)

**Важно:** 
- **Метаданные** остаются в **человекочитаемом JSON**
- **Данные (payload)** сжимаются **нашим кастомным кодером**

---

### ⚠️ GZIP формат (для совместимости с gunzip)

**Файл:** `app/src/main/kotlin/com/customDB/compression/GzipFormat.kt`

**Что делает:**
- Использует **готовый** `java.util.zip.GZIPOutputStream` для создания GZIP файла
- Использует **готовый** `java.util.zip.GZIPInputStream` для распаковки
- Это нужно для совместимости с gunzip (требование лабораторной работы)

**Примечание:** 
- Не используется в базе данных (там используется `CustomCompressionFormat`)
- Используется для демонстрации совместимости с внешними утилитами (gunzip, 7z)

---

### ❌ Не используется сейчас

**Файлы:**
- `DeflateEncoder.kt` - не используется (оставлен для истории)
- `BitWriter.kt` / `BitReader.kt` - не используются (оставлены для истории)

---

## Текущая архитектура

### В базе данных (основное использование):

```
INSERT:
  Row (объект) → JSON сериализация → payload (JSON строка)
    → если payload > 50 байт → CustomCompressionFormat.compress()
      → LZ77.compress() → токены (Literal/Reference)
      → LZ77Encoder.encode() → бинарный формат (наш формат)
    → Base64.encode() → JSON поле "payload"
    → RecordLine (JSON: id, tombstone, payload, compressed) → файл .tbl

SELECT:
  Файл .tbl → JSON строка RecordLine
    → если "compressed": true → Base64.decode()
      → CustomCompressionFormat.decompress()
        → LZ77Encoder.decode() → токены
        → LZ77.decompress() → исходный payload (JSON строка)
    → JSON десериализация → Row (объект)
```

### Для GZIP совместимости (демонстрация):

```
Данные → GzipFormat.compress()
  → использует GZIPOutputStream (готовое)
  → GZIP файл (совместим с gunzip)
```

---

## Текущее состояние

**Что реализовано самостоятельно:**
- ✅ **LZ77 алгоритм** (`LZ77.kt`) - полностью наша реализация
- ✅ **Формат хранения** (`LZ77Encoder.kt`) - полностью наш формат
- ✅ **Интеграция в БД** (`Codec.kt`) - использует наш кодер для payload

**Что использует готовые библиотеки:**
- ⚠️ `GzipFormat` использует `GZIPOutputStream` / `GZIPInputStream` (для совместимости с gunzip)
- ⚠️ `GzipFormat` **НЕ используется в базе данных** (только для демонстрации совместимости с gunzip)
- ✅ В базе данных используется **только** `CustomCompressionFormat` (наш LZ77 + наш LZ77Encoder)

**Что работает:**
- ✅ База данных сжимает данные нашим алгоритмом
- ✅ База данных распаковывает данные нашим алгоритмом
- ✅ Метаданные остаются читаемыми в JSON
- ✅ GZIP файлы создаются и распаковываются gunzip (для демонстрации)
- ✅ Все тесты проходят

---

## Соответствие заданию

✅ **Алгоритм сжатия реализован самостоятельно** (LZ77)
✅ **Формат хранения реализован самостоятельно** (LZ77Encoder)
✅ **Интегрировано в базу данных** (Codec.kt использует CustomCompressionFormat)
✅ **gunzip успешно распаковывает** GZIP файлы (для демонстрации совместимости)
✅ **Сравнение с готовыми библиотеками** выполнено

**Реализация полностью соответствует требованиям задания!**

