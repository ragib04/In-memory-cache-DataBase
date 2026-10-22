package com.example.cache.controller;

import com.example.cache.exception.CommandException;
import com.example.cache.service.Functions;
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
public class Controller {
    private final Functions functions;

    public Controller(Functions functions) {
        this.functions = functions;

    }

    @GetMapping("/")
    public String welcome() {
        return "Hello!!! Welcome to REDIS_CLONE!!!";
     }

    @GetMapping("/curr_status")
    public String status() {
        return functions.currentStatus();

    }

    @GetMapping("/api/command")
    public String queryCommand(@RequestParam String value) {
        return functions.execute(value);
    }

    @GetMapping("/{command:.+}")
    public String compatibilityCommand(@PathVariable("command") String command) {
        return functions.execute(command);
        
    }

    @PostMapping("/api/command")
    public CommandResponse executeCommand(@RequestBody CommandRequest request) {
        String command = String.join(" ", request.command(), String.join(" ", request.arguments()));
        return new CommandResponse(functions.execute(command));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(CommandException.class)
    public ResponseEntity<CommandResponse> handleCommandError(CommandException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new CommandResponse(exception.getMessage()));
    }
    public record CommandRequest(String command, java.util.List<String> arguments) {
        public CommandRequest {
            if (command == null || arguments == null) {
                throw new CommandException("Request Invalid");
            }
        }
    }

    public record CommandResponse(String result) {
    }
}
