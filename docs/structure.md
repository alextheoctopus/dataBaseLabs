---

## 📂 Project Structure

```
lab-rdb/
 ├── build.gradle                  
 ├── settings.gradle              
 ├── gradlew / gradlew.bat         
 ├── gradle/
 └── src/
     ├── main/
     │   └── java/
     │       └── com/example/rdb/
     │           ├── api/          ← ЛР-1: интерфейсы и DTO
     │           │    ├── IRelationalDatabase.java
     │           ├── storage/      ← ЛР-2: страницы, heap-файлы, буферный пул
     │           ├── parser/       ← ЛР-3: SQL-парсер и AST
     │           ├── planner/      ← ЛР-3/4: планировщик запросов
     │           ├── exec/         ← ЛР-2+: исполнение операторов
     │           └── catalog/      ← ЛР-2: метаданные о таблицах
     │
     └── test/
         └── java/
             └── com/example/rdb/tests/  ← Тесты
                 
```

---
