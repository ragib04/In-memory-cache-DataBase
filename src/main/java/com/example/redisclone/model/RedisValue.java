package com.example.redisclone.model;

public sealed interface RedisValue permits StringValue, SortedSetValue {
}
