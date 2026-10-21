package com.example.redisclone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RedisCloneApplication {
    public static void main(String[] args) {
        SpringApplication.run(RedisCloneApplication.class, args);
    }
}
