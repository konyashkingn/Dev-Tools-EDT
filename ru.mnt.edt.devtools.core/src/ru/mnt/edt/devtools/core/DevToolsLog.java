package ru.mnt.edt.devtools.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Отладочный журнал плагина в отдельном файле ~/mnt-devtools.log.
 * Журнал EDT (.metadata/.log) не подходит: EDT не записывает в него информационные сообщения.
 * Файл очищается, когда превышает {@link #MAX_SIZE}.
 */
public final class DevToolsLog {

    private static final Path FILE = Path.of(System.getProperty("user.home"), "mnt-devtools.log"); //$NON-NLS-1$
    private static final long MAX_SIZE = 2L * 1024 * 1024;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS"); //$NON-NLS-1$

    private DevToolsLog() {
    }

    public static synchronized void log(String message) {
        try {
            if (Files.exists(FILE) && Files.size(FILE) > MAX_SIZE) {
                Files.delete(FILE);
            }
            String line = LocalTime.now().format(TIME) + ' ' + message + System.lineSeparator();
            Files.writeString(FILE, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            // журнал не должен мешать работе плагина
        }
    }
}
