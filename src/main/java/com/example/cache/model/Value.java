package com.example.cache.model;

public sealed interface Value permits StringValue, SortedSetValue {
}
