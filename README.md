# dataBaseLabs
Team: Evgeniy, Danil, Aleksandra
шардирование по id.

Делаю два шарда (%2==0||%2==1).

**Router** — компонент, который получает SQL-запрос и решает, **в какой шард** его отправить.

SELECT * FROM users WHERE user_id = 3;

Router вычисляет:

```
3 % 2 == 1
```

→ шард

```
s1
```

→ отправляем запрос на сервер 10.0.0.2.

UPDATE users SET balance = 80 WHERE user_id = 4;
Router: 4 % 2 == 0 → шард s0 → сервер 10.0.0.1.

---

# Репликация

Возьмём **один шард** (s0):

| Роль | Сервер | Назначение |
| --- | --- | --- |
| master | 10.0.0.1 | принимает записи (INSERT/UPDATE/DELETE) |
| replica1 | 10.0.0.3 | только чтение (SELECT) |
| replica2 | 10.0.0.4 | только чтение (SELECT) |
- Клиент (или Router) отправляет запрос:

    ```sql
    INSERT INTO users VALUES (5, 'Petr', 'Tula', 100);
    
    ```

- Мастер применяет изменение и записывает его в **журнал (WAL)**.
- Репликатор (наш `PrimaryReplicatorHttp`) передаёт **RepBatch** с этой операцией на все реплики.
- Реплика получает `/repl/push`, применяет изменения через `ReplicaApplierHttp`.

```
				         ┌──────────────────────────┐
				         │        Router            │
				         │  (находит нужный шард)   │
				         └──────────┬───────────────┘
				                    │
				 ┌──────────────────┴──────────────────┐
				 │                                     │
┌───────────────┐                     ┌───────────────┐
│   Shard s0    │                     │   Shard s1    │
│   (user_id%2=0)│                    │  (user_id%2=1)│
├───────────────┤                     ├───────────────┤
│ master:10.0.1 │◄───replication─────┤ master:10.0.2 │
│ Replica:10.0.3│                     │ Replica:10.0.4│
└───────────────┘                     └───────────────┘
```

Порядок работы программы:

Main.kt - точка входа. Парсит аргументы (—role,—port,—shardId,—replicas,—router)

В зависимости от роли: запускает Router или запускает узел бд (master/replica)

---

**master шарда**

LocalStorageEngine-Создает .meta, .tbl локальное хранилище файлов.

PrimaryReplicationHttp(sharfId, replicas) - готовит репликацию

SqlEngine (storage,shardId,replicator) - ядро, которое будет выполнять SQL и публиковать изменения

SqlHttpServer.start(port,sqlEngine)-запускает HTTP сервер для клиентов (Router)

ReplicaApplierHttp.startHttp(replPort) - поднимает простой /repl/heartbeat endpoint (для статуса)

---

**replica - получает изменения от Мастера через HTTP POST /repl/push**

LocalStorageEngine - открывает локальные таблицы

SqlEngine(storage,shardId,null) - движок без репликатора (реплика не публикует)

SqlHttpServer.start(port, sqlEngine) - слушает /repl/push и /repl/heartbeat

---

**router - принимает /execute и /query. По id решает какой шард нужен. Отправляет запрос на соответствующий Мастер и реплику.**

ClusterState.load(cluster.json) - загружает карту всех шардов

ShardLocator(cluster,shardKey) - определяет по SQL, какой шард нужен

RouterHttpServer.start(routerPort) - запускат прокси, который перенаправояет запросы

---

SqlHttpServer принимает HTTP POST /execute → SqlEngine.execute(sql)

↓

SqlEngine.execute(sql): Парсит SQL через CCJSqlParserUtil.parse(sql);  вызывает нужный метод (handleInsert,handleUpdate,…); выполняет операцию над таблицей через LocalStorageEngine/table

↓

Если операция Create.Insert,Update,Delete → SqlEngine вызывает replicator.publish(RepBatch(…))

↓

На реплике replicaApplierHttp: получает /repl/push, десериализует RepBatch, применяет операции (Insert,Upsert,Delete) через LocalStorageEngine
| Компонент | Что делает |
| --- | --- |
| **Клиент** | Отправляет HTTP `/execute` на Router |
| **RouterHttpServer** | Определяет `shardId` по `id=1`, находит Мастера `s0`, пересылает туда
*передавать id в строке |
| **SqlHttpServer (master s0)** | Принимает запрос, вызывает `SqlEngine.execute(sql)` |
| **SqlEngine** | Парсит SQL, вызывает `handleInsert()` |
| **LocalStorageEngine + Table** | Пишет данные в `users.tbl` |
| **SqlEngine.publish(RepOp.Insert)** | Формирует батч `RepBatch` |
| **PrimaryReplicatorHttp** | POST `/repl/push` на реплику |
| **ReplicaApplierHttp (replica s0)** | Применяет вставку локально |
| **SELECT-запросы** могут идти уже на реплику (Router направляет их туда) |  |
Запуск 
```
gradle run
```
Остановить процесс 
```netstat -ano | findstr :8080```
```taskkill /PID 14872 /F```

Тест:
# DDL фан-аут через роутер
```curl.exe -X POST http://localhost:8080/execute -d "CREATE TABLE users (id INT, name STRING, city STRING, balance LONG);"```

# DML в разные шарды
```curl.exe -X POST http://localhost:8080/execute -d "INSERT INTO users (id,name,city,balance) VALUES (1,'Anna','A',100);"```
```curl.exe -X POST http://localhost:8080/execute -d "INSERT INTO users (id,name,city,balance) VALUES (700,'Ivan','B',5000);"```

# SELECT (чтения должны идти в живые реплики)
```curl.exe -X POST http://localhost:8080/query -d "SELECT * FROM users WHERE id=1;"```
```curl.exe -X POST http://localhost:8080/query -d "SELECT * FROM users WHERE id=700;"```

# Хартбиты репликации (порт = sql+1000)
```curl.exe http://localhost:9002/repl/heartbeat```
```curl.exe http://localhost:9102/repl/heartbeat```