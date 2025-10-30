package com.customDB.server

import com.customDB.api.LocalStorageEngine
import com.customDB.api.SqlEngine
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

class SqlHttpServerTest {

    private lateinit var engine: LocalStorageEngine
    private lateinit var sql: SqlEngine
    private lateinit var serverWrapper: SqlHttpServer
    private lateinit var httpServer: HttpServer
    private lateinit var client: HttpClient
    private var port: Int = -1

    @BeforeEach
    fun setUp(@org.junit.jupiter.api.io.TempDir tempDir: Path) {
        engine = LocalStorageEngine(tempDir.toFile())
        sql = SqlEngine(engine)
        serverWrapper = SqlHttpServer(engine, sql)
        httpServer = serverWrapper.start(0)
        port = httpServer.address.port
        client = HttpClient.newHttpClient()
    }

    @AfterEach
    fun tearDown() {
        serverWrapper.stop(0)
        engine.close()
    }

    @Test
    fun `health endpoint works`() {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/health"))
            .GET()
            .build()

        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, resp.statusCode())
        assertTrue(resp.body().contains("\"ok\""))
    }

    @Test
    fun `full CRUD over HTTP`() {
        fun postSql(sqlText: String): HttpResponse<String> {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/query"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(sqlText))
                .build()
            return client.send(req, HttpResponse.BodyHandlers.ofString())
        }

        var resp = postSql("CREATE TABLE Users (id INT, name STRING, age INT)")
        assertEquals(200, resp.statusCode(), "CREATE should be 200")

        resp = postSql("INSERT INTO Users (id, name, age) VALUES (1, 'Alice', 25)")
        assertEquals(200, resp.statusCode())
        resp = postSql("INSERT INTO Users (id, name, age) VALUES (2, 'Bob', 30)")
        assertEquals(200, resp.statusCode())

        resp = postSql("SELECT * FROM Users WHERE name = 'Alice'")
        assertEquals(200, resp.statusCode())
        val bodyAlice = resp.body()
        assertTrue(bodyAlice.startsWith("["), "Should be JSON array")
        assertTrue(bodyAlice.contains("\"name\""))
        assertTrue(bodyAlice.contains("\"Alice\""))
        assertTrue(bodyAlice.contains("\"age\""))
        assertTrue(bodyAlice.contains("25"))

        resp = postSql("DELETE FROM Users WHERE name = 'Bob'")
        assertEquals(200, resp.statusCode())
        resp = postSql("SELECT * FROM Users WHERE name = 'Bob'")
        assertEquals(200, resp.statusCode())
        assertTrue(resp.body().trim() == "[]" || !resp.body().contains("Bob"), "Bob should be gone")

        resp = postSql("UPDATE Users SET age = 26 WHERE name = 'Alice'")
        assertEquals(200, resp.statusCode())
        resp = postSql("SELECT * FROM Users WHERE name = 'Alice'")
        assertEquals(200, resp.statusCode())
        assertTrue(resp.body().contains("26"))
    }

    @Test
    fun `SELECT multiple rows over HTTP`() {
        fun postSql(sqlText: String): HttpResponse<String> {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/query"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(sqlText))
                .build()
            return client.send(req, HttpResponse.BodyHandlers.ofString())
        }

        var resp = postSql("CREATE TABLE People (id INT, name STRING, age INT)")
        assertEquals(200, resp.statusCode())

        postSql("INSERT INTO People (id, name, age) VALUES (1, 'Alex', 20)")
        postSql("INSERT INTO People (id, name, age) VALUES (2, 'Alex', 20)")
        postSql("INSERT INTO People (id, name, age) VALUES (3, 'Mary', 21)")

        resp = postSql("SELECT * FROM People WHERE age = 20")
        assertEquals(200, resp.statusCode())
        val body = resp.body()

        val rowCount = Regex("""\{\s*"values"\s*:""").findAll(body).count()
        assertEquals(2, rowCount, "SELECT should return exactly two rows")

        val alexCount = Regex(""""Alex"""").findAll(body).count()
        assertEquals(2, alexCount, "Both rows should have name 'Alex'")

        assertTrue(Regex(""""id"\s*:\s*\{[^}]*"v"\s*:\s*1""").containsMatchIn(body), "id=1 should be present")
        assertTrue(Regex(""""id"\s*:\s*\{[^}]*"v"\s*:\s*2""").containsMatchIn(body), "id=2 should be present")

        val maryCount = Regex(""""Mary"""").findAll(body).count()
        assertEquals(0, maryCount, "Row with Mary(age=21) must not be in the result")
    }



}
