# Java Redis Clone

An educational Java 17 and Spring Boot reimplementation of this repository's Python Redis-like clone. It is a small in-memory key-value store with a REST API and append-only command log; it is not the official Redis server and does not require an external Redis installation or database.

The Python implementation and its `README.md` remain separate. This guide describes the Java application in `java-redis-clone/`.

## Contents

- [Requirements and start-up](#requirements-and-start-up)
- [REST API](#rest-api)
- [Commands](#commands)
- [Expiration and cleanup](#expiration-and-cleanup)
- [Persistence](#persistence)
- [Implementation overview](#implementation-overview)
- [Tests](#tests)
- [Current limitations](#current-limitations)

## Requirements and start-up

- Java 17 or later
- Maven

From the repository root, run:

```bash
cd java-redis-clone
mvn spring-boot:run
```

The server listens at `http://localhost:5002`. Stop it with `Ctrl+C`.

## REST API

### POST command endpoint

Send the command name and its arguments as JSON:

```bash
curl -X POST http://localhost:5002/api/command \
  -H 'Content-Type: application/json' \
  -d '{"command":"SET","arguments":["name","John"]}'
```

Successful responses use the shape `{"result":"OK"}`. Commands that produce an error return HTTP 400 with an error result.

### GET command endpoint

The query endpoint accepts a URL-encoded command string and returns the result as plain text:

```bash
curl 'http://localhost:5002/api/command?value=GET%20name'
```

The original-style `GET /<command>` endpoint is also available when the command is URL encoded. `GET /` returns a welcome message; `GET /curr_status` shows the in-memory database state.

## Commands

Command names are case-insensitive. Arguments are sent as strings in the JSON array. For examples below, each request uses `POST http://localhost:5002/api/command` with `{"command":"COMMAND","arguments":[...]}`.

| Command | Arguments | Result / behavior |
| --- | --- | --- |
| `PING` | `[]` | Returns `PONG`. |
| `SET` | `key value [EX seconds\|PX milliseconds] [NX\|XX] [KEEPTTL]` | Stores or replaces a string, optionally with expiry or conditions. Returns `OK`; a failed `NX`/`XX` condition returns `None`. |
| `GET` | `key` | Returns the string value, `None` if missing, and a clear error if the key contains a sorted set. |
| `DEL` / `DELETE` | `key` | Deletes a key and its expiration metadata; returns `1` if deleted and `0` if absent. |
| `EXPIRE` | `key seconds` | Sets a relative expiry on a string or sorted set; returns `1` if the key exists, otherwise `0`. |
| `TTL` | `key` | Returns the remaining lifetime in seconds as a number, or `None` for an absent key or a key without an expiry. |
| `ZADD` | `key [NX\|XX] [CH] score member [score member ...]` | Adds members or updates scores. Returns the number added, or the number changed with `CH`. Supports `NX`, `XX`, and `CH`. |
| `ZRANGE` | `key start stop [WITHSCORES]` | Lists members in ascending score order; equal scores sort by member name. |
| `ZREVRANGE` | `key start stop [WITHSCORES]` | Lists members in descending score order. |
| `ZRANK` | `key member` | Returns the member's zero-based ascending rank, or `None`. |
| `ZREVRANK` | `key member` | Returns the member's zero-based descending rank, or `None`. |

Range indexes are zero-based. Negative indexes count backward from the end; `0 -1` selects the full set. Results are rendered as comma-separated text. With `WITHSCORES`, each item is represented as `score member`.

Examples:

```bash
# Health check
curl -X POST http://localhost:5002/api/command \
  -H 'Content-Type: application/json' \
  -d '{"command":"PING","arguments":[]}'

# Set a key for 60 seconds, then read it
curl -X POST http://localhost:5002/api/command \
  -H 'Content-Type: application/json' \
  -d '{"command":"SET","arguments":["name","Ragib","EX","60"]}'
curl -X POST http://localhost:5002/api/command \
  -H 'Content-Type: application/json' \
  -d '{"command":"GET","arguments":["name"]}'

# Sorted set: add, list with scores, and check rank
curl -X POST http://localhost:5002/api/command \
  -H 'Content-Type: application/json' \
  -d '{"command":"ZADD","arguments":["scores","2","Bob","1","Alice"]}'
curl -X POST http://localhost:5002/api/command \
  -H 'Content-Type: application/json' \
  -d '{"command":"ZRANGE","arguments":["scores","0","-1","WITHSCORES"]}'
curl -X POST http://localhost:5002/api/command \
  -H 'Content-Type: application/json' \
  -d '{"command":"ZRANK","arguments":["scores","Alice"]}'

# Set expiry on an existing key, inspect TTL, then delete it
curl -X POST http://localhost:5002/api/command \
  -H 'Content-Type: application/json' \
  -d '{"command":"EXPIRE","arguments":["name","30"]}'
curl -X POST http://localhost:5002/api/command \
  -H 'Content-Type: application/json' \
  -d '{"command":"TTL","arguments":["name"]}'
curl -X POST http://localhost:5002/api/command \
  -H 'Content-Type: application/json' \
  -d '{"command":"DEL","arguments":["name"]}'
```

## Expiration and cleanup

`SET` accepts `EX` in seconds and `PX` in milliseconds. `KEEPTTL` preserves an existing key's expiry when replacing its value. `EXPIRE` applies to strings and sorted sets. `TTL` reports a floating-point number of seconds remaining.

Expired keys are removed lazily when a command accesses them. A scheduled cleanup also samples up to ten keys per pass, for up to ten passes, when the database contains more than 100 keys. This follows the Python clone's cleanup policy; unsampled expired keys may remain until a later cleanup or access.

## Persistence

Successful mutating commands are appended with their Unix timestamp to `java-redis-clone/java-command.log`. At startup, the application replays those commands to rebuild its in-memory state using their original times, so elapsed expiration time is preserved and already-expired keys are removed when accessed or sampled. Replayed commands are not appended again, and malformed lines are skipped. The log is Java-specific; the Python app uses the root `log.txt`.

Older Java log entries without timestamps are still replayed, using the current time as their starting point. The log is append-only and is not compacted; there are no snapshots or log rotation.

## Implementation overview

- `RedisService` stores values in a `Map`, tracks expiry deadlines separately, and serializes command execution.
- `StringValue` and `SortedSetValue` represent supported value types.
- Sorted sets use a `TreeSet` ordered by score and then member name, with a member-to-score map for updates.
- `PersistenceService` synchronizes appends and replays the command log at startup.
- `RedisController` exposes the JSON POST and query-string GET endpoints.

## Tests

Run the JUnit 5 tests from the Java module:

```bash
cd java-redis-clone
mvn test
```

## Current limitations

- The Python project's `ZADD INCR` path is unfinished and returns a syntax error; Java matches that behavior. `NX`, `XX`, and `CH` are supported.
- Commands are split on whitespace, so keys and values containing spaces are not supported.
- Cleanup uses lazy deletion plus scheduled sampling when the database has more than 100 keys.
- Historical log entries from before timestamp support restart their expiration period at replay.
- This educational clone does not implement every Redis command or protocol feature.
