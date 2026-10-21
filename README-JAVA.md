# Java Redis Clone

This is a Java 17 and Spring Boot implementation of the Redis-like cache in the original Python project. It provides basic Redis-style commands through a REST API. It is an educational project and is not the official Redis server.

## Table of Contents

1. Usage
2. Overview
3. Persistence
4. Functions Implemented
   - GET
   - SET
   - EXPIRE
   - ZADD
   - ZRANGE
   - ZRANK
   - DEL / DELETE
   - ZREVRANK
   - ZREVRANGE
   - TTL
   - PING
5. Tests
6. Limitations

## Usage

Requirements:

- Java Development Kit (JDK) 17 or later
- Apache Maven

Project dependencies are managed in `pom.xml`; Python packages are not required.

From the project directory, start the application:

```bash
mvn spring-boot:run
```

The server listens at [http://localhost:5002](http://localhost:5002). Stop it with `Ctrl+C`.

Commands can be sent with a JSON `POST` request to `/api/command`. For example:

```bash
curl -X POST http://localhost:5002/api/command \
  -H 'Content-Type: application/json' \
  -d '{"command":"SET","arguments":["name","Ragib"]}'
```

The response is `{"result":"OK"}`. To read the key, send:

```bash
curl -X POST http://localhost:5002/api/command \
  -H 'Content-Type: application/json' \
  -d '{"command":"GET","arguments":["name"]}'
```

A query-string endpoint is also available:

```bash
curl 'http://localhost:5002/api/command?value=GET%20name'
```

## Overview

This project implements 11 Redis-like commands using Java collections and Spring Web. String values and sorted sets are held in memory. Sorted-set members are ordered by score and then member name, allowing range and rank queries.

### Persistence

The application uses an append-only command log to rebuild its in-memory data when it starts:

1. Successful mutating commands are appended with a timestamp to `java-command.log` in the Java project directory.
2. At startup, logged commands are replayed without being written to the log again. Their timestamps preserve expiration deadlines across restarts.
3. Malformed log entries are skipped. Older entries without timestamps are also replayed, using the current time.

The Java log is separate from the Python project's `log.txt`. The log is not compacted, and snapshots are not implemented.

## Functions Implemented

Commands are case-insensitive. For JSON requests, put command arguments in the `arguments` array. Successful responses are returned in a `result` field; command errors return HTTP 400.

### 1. GET

**Command:** `GET key`

Returns the string stored at the key. Returns `None` if the key is missing. Returns a type error if the key contains a sorted set.

### 2. SET

**Command:** `SET key value [EX seconds|PX milliseconds] [NX|XX] [KEEPTTL]`

Stores a string value, replacing an existing value by default. `EX` and `PX` set expiration in seconds and milliseconds. `NX` sets only if the key does not exist; `XX` sets only if it does. `KEEPTTL` preserves an existing expiration when replacing the value.

Example:

```bash
curl -X POST http://localhost:5002/api/command \
  -H 'Content-Type: application/json' \
  -d '{"command":"SET","arguments":["name","Ragib","EX","60"]}'
```

### 3. EXPIRE

**Command:** `EXPIRE key seconds`

Sets an expiration on an existing string or sorted-set key. Returns `1` when the key exists and `0` otherwise.

Expired keys are removed lazily when accessed. A scheduled cleanup also samples keys when the database contains more than 100 keys, matching the Python project's cleanup threshold.

### 4. ZADD

**Command:** `ZADD key [NX|XX] [CH] score member [score member ...]`

Adds members to a sorted set or updates their scores. Members are ordered by score, then lexicographically by name. `NX`, `XX`, and `CH` are supported. The Python project's `INCR` option is unfinished and returns a syntax error; Java matches that behavior.

### 5. ZRANGE

**Command:** `ZRANGE key start stop [WITHSCORES]`

Returns members from low to high score. Equal scores are ordered by member name. Indexes are zero-based; negative indexes count from the end. Use `0 -1` to select the full set. `WITHSCORES` includes each score.

### 6. ZRANK

**Command:** `ZRANK key member`

Returns the member's zero-based rank in ascending score order, or `None` if the key or member is missing.

### 7. DEL / DELETE

**Command:** `DEL key` (also accepts `DELETE key`)

Deletes the key and its expiration metadata. Returns `1` if a key was removed and `0` if it was absent.

### 8. ZREVRANK

**Command:** `ZREVRANK key member`

Returns the member's zero-based rank in descending score order, or `None` if the key or member is missing.

### 9. ZREVRANGE

**Command:** `ZREVRANGE key start stop [WITHSCORES]`

Returns a sorted-set range from high to low score. Indexes are zero-based and negative indexes count from the end.

### 10. TTL

**Command:** `TTL key`

Returns the remaining lifetime in seconds. Returns `None` if the key is missing or has no expiration.

### 11. PING

**Command:** `PING`

Checks that the server is running and returns `PONG`.

## Tests

Run the JUnit 5 tests from the project directory:

```bash
mvn test
```

## Limitations

- This project implements the commands listed above, not the full Redis command set or Redis network protocol.
- Keys and values containing spaces are not supported by the whitespace-based command parser.
- Cleanup samples keys only when the in-memory database has more than 100 entries; expired, unsampled keys may remain until a later cleanup or access.
- The append-only log is not compacted or rotated, and snapshots are not implemented.
