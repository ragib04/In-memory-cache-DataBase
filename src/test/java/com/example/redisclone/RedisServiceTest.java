package com.example.redisclone;

import com.example.redisclone.service.PersistenceService;
import com.example.redisclone.service.RedisService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.example.redisclone.exception.RedisCommandException;
import java.nio.file.Files;
import java.nio.file.Path;

class RedisServiceTest {
    private RedisService service() {
        return new RedisService(new PersistenceService("target/test-log.txt"));
    }

    @Test
    void supportsStringCommands() {
        RedisService service = service();
        assertEquals("OK", service.execute("SET name John"));
        assertEquals("John", service.execute("GET name"));
        assertEquals("1", service.execute("DEL name"));
        assertEquals("None", service.execute("GET name"));
    }

    @Test
    void supportsSortedSetCommands() {
        RedisService service = service();
        assertEquals("2", service.execute("ZADD scores 2 Bob 1 Alice"));
        assertEquals("Alice, Bob", service.execute("ZRANGE scores 0 -1"));
        assertEquals("2 Bob, 1 Alice", service.execute("ZREVRANGE scores 0 -1 WITHSCORES"));
        assertEquals("0", service.execute("ZRANK scores Alice"));
        assertEquals("0", service.execute("ZREVRANK scores Bob"));
    }

    @Test
    void supportsExpiration() throws InterruptedException {
        RedisService service = service();
        assertEquals("OK", service.execute("SET temporary value PX 20"));
        Thread.sleep(30);
        assertEquals("None", service.execute("GET temporary"));
    }

    @Test
    void supportsSetOptionsAndTtl() {
        RedisService service = service();
        assertEquals("OK", service.execute("SET opt one EX 10"));
        assertEquals("None", service.execute("SET opt two NX"));
        assertEquals("OK", service.execute("SET opt two XX KEEPTTL"));
        assertEquals("two", service.execute("GET opt"));
        assertEquals(true, Double.parseDouble(service.execute("TTL opt")) > 0);
        assertEquals("1", service.execute("EXPIRE opt 5"));
    }

    @Test
    void supportsSetPxAndSortedSetExpiration() {
        RedisService service = service();
        assertEquals("OK", service.execute("SET px short PX 10000"));
        assertEquals("1", service.execute("ZADD exp 1 a"));
        assertEquals("1", service.execute("EXPIRE exp 10"));
        assertEquals(true, Double.parseDouble(service.execute("TTL exp")) > 0);
    }

    @Test
    void reportsTypeAndSyntaxErrorsClearly() {
        RedisService service = service();
        service.execute("ZADD key 1 member");
        assertEquals("Returned value is not String", assertThrows(RedisCommandException.class,
                () -> service.execute("GET key")).getMessage());
        assertEquals("ERR Unknown or disabled command \"NOPE\"", service.execute("NOPE"));
    }

    @Test
    void replaysPersistenceLogWithoutRewritingIt() throws Exception {
        Path file = Path.of("target/replay-test.log");
        Files.writeString(file, "SET replayed yes\nmalformed\n");
        RedisService replayed = new RedisService(new PersistenceService(file.toString()));
        replayed.load();
        assertEquals("yes", replayed.execute("GET replayed"));
        assertEquals("SET replayed yes\nmalformed\n", Files.readString(file));
    }

    @Test
    void timestampedReplayPreservesExpiryDeadline() throws Exception {
        Path file = Path.of("target/replay-expiry-test.log");
        Files.writeString(file, "1000.0 SET alreadyExpired value EX 10\n");
        RedisService replayed = new RedisService(new PersistenceService(file.toString()));
        replayed.load();
        assertEquals("1000.0 SET alreadyExpired value EX 10\n", Files.readString(file));
        assertEquals("None", replayed.execute("GET alreadyExpired"));
        assertEquals(true, Files.readString(file).contains(" DELETE alreadyExpired\n"));
    }

    @Test
    void matchesPythonCloneUnsupportedZaddIncrBehavior() {
        RedisService service = service();
        RedisCommandException error = assertThrows(RedisCommandException.class,
                () -> service.execute("ZADD scores 2 Bob INCR"));
        assertEquals("Syntax Error in Command", error.getMessage());
    }
}
