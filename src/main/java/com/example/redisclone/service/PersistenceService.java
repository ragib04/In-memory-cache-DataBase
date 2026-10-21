package com.example.redisclone.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Service
public class PersistenceService {
    private final Path logFile;
    private final Object lock = new Object();

    public PersistenceService(@Value("${redis.clone.log-file:java-command.log}") String file) {
        this.logFile = Path.of(file);
    }

    public void append(String command) {
        append(System.currentTimeMillis() / 1000.0, command);
    }

    public void append(double timestampSeconds, String command) {
        synchronized (lock) {
            try {
                Path parent = logFile.toAbsolutePath().getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.writeString(logFile, timestampSeconds + " " + command + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) { throw new IllegalStateException("Could not write command log", e); }
        }
    }

    public void replay(Consumer<String> replayCommand) {
        replayTimed((timestamp, command) -> replayCommand.accept(command));
    }

    public void replayTimed(BiConsumer<Double, String> replayCommand) {
        synchronized (lock) {
            if (!Files.exists(logFile)) return;
            try {
                List<String> lines = Files.readAllLines(logFile);
                for (String line : lines) {
                    if (line.isBlank()) continue;
                    String command = line;
                    double timestamp = System.currentTimeMillis() / 1000.0;
                    int split = line.indexOf(' ');
                    if (split > 0) {
                        try { timestamp = Double.parseDouble(line.substring(0, split)); command = line.substring(split + 1); }
                        catch (NumberFormatException ignored) { }
                    }
                    try { replayCommand.accept(timestamp, command); } catch (RuntimeException ignored) { /* malformed historical command */ }
                }
            } catch (IOException e) { throw new IllegalStateException("Could not read command log", e); }
        }
    }
}
