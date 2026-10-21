package com.example.redisclone.controller;

import com.example.redisclone.exception.RedisCommandException;
import com.example.redisclone.service.RedisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedisController {
    private final RedisService redisService;

    public RedisController(RedisService redisService) {
        this.redisService = redisService;
    }

    @GetMapping("/")
    public String welcome() {
        return "Hello!!! Welcome to REDIS_CLONE!!!";
    }

    @GetMapping("/curr_status")
    public String status() {
        return redisService.currentStatus();
    }

    @GetMapping("/api/command")
    public String queryCommand(@RequestParam String value) {
        return redisService.execute(value);
    }

    @GetMapping("/{command:.+}")
    public String compatibilityCommand(@PathVariable("command") String command) {
        return redisService.execute(command);
    }

    @PostMapping("/api/command")
    public CommandResponse executeCommand(@RequestBody CommandRequest request) {
        String command = String.join(" ", request.command(), String.join(" ", request.arguments()));
        return new CommandResponse(redisService.execute(command));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(RedisCommandException.class)
    public ResponseEntity<CommandResponse> handleCommandError(RedisCommandException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new CommandResponse(exception.getMessage()));
    }

    public record CommandRequest(String command, java.util.List<String> arguments) {
        public CommandRequest {
            if (command == null || arguments == null) {
                throw new RedisCommandException("Request Invalid");
            }
        }
    }

    public record CommandResponse(String result) {
    }
}
