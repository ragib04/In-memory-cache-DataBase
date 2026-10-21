package com.example.redisclone.exception;

public class RedisCommandException extends RuntimeException {
    public RedisCommandException(String message) {
        super(message);
    }
}
