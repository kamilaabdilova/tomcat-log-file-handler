package org.example.tomcatlogfilehandler.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Getter
@Setter
public class TomcatLogFileHandlerService {
    private Path lastUploadedFilePath;
    private static final String LOG_REGEX = "(\\d{2}-\\w{3}-\\d{4} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(INFO|SEVERE|WARNING|DEBUG|ERROR|TRACE)\\s+\\[(.+?)\\]\\s+([\\w\\.]+)\\s*[:-]?\\s*(.*)";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss.SSS", Locale.ENGLISH);
    private final FileManagementService fileManagementService;

    @Autowired
    public TomcatLogFileHandlerService(FileManagementService fileManagementService) throws IOException {
        this.fileManagementService = fileManagementService;
        this.lastUploadedFilePath = fileManagementService.getLastUploadedFilePath();
        if (this.lastUploadedFilePath != null) {
            System.out.println("Restored last uploaded file: " + this.lastUploadedFilePath);
        }
    }

    public String uploadLogFile(MultipartFile file, String logDirectory) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty.");
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ENGLISH));
        Path targetLocation = Path.of(logDirectory, "catalina_" + timestamp + ".out");

        Files.createDirectories(targetLocation.getParent());
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        this.lastUploadedFilePath = targetLocation;
        fileManagementService.saveLastUploadedFilePath(targetLocation);
        System.out.println("File uploaded to: " + this.lastUploadedFilePath);
        return this.lastUploadedFilePath.getFileName().toString();
    }

    public Map<String, String> parseLogLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        Pattern pattern = Pattern.compile(LOG_REGEX);
        Matcher matcher = pattern.matcher(line);
        if (matcher.matches()) {
            Map<String, String> logEntry = new HashMap<>();
            logEntry.put("timestamp", matcher.group(1));
            logEntry.put("level", matcher.group(2));
            logEntry.put("thread", matcher.group(3));
            logEntry.put("logger", matcher.group(4));
            logEntry.put("message", matcher.group(5));
            return logEntry;
        }
        return null;
    }

    public Map<String, Object> getLogSummary() {
        if (lastUploadedFilePath == null) {
            return new HashMap<>();
        }
        try {
            Map<String, Integer> levelCounts = new HashMap<>();
            LocalDateTime firstTimestamp = null;
            LocalDateTime lastTimestamp = null;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss.SSS", Locale.ENGLISH);

            for (String line : Files.readAllLines(lastUploadedFilePath)) {
                Map<String, String> logEntry = parseLogLine(line);
                if (logEntry != null) {
                    String level = logEntry.get("level");
                    levelCounts.put(level, levelCounts.getOrDefault(level, 0) + 1);

                    LocalDateTime timestamp = LocalDateTime.parse(logEntry.get("timestamp"), formatter);
                    if (firstTimestamp == null || timestamp.isBefore(firstTimestamp)) {
                        firstTimestamp = timestamp;
                    }
                    if (lastTimestamp == null || timestamp.isAfter(lastTimestamp)) {
                        lastTimestamp = timestamp;
                    }
                }
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("fileName", lastUploadedFilePath.getFileName().toString());
            summary.put("levelCounts", levelCounts);
            if (firstTimestamp != null && lastTimestamp != null) {
                Duration duration = Duration.between(firstTimestamp, lastTimestamp);
                summary.put("timeRange", Map.of(
                        "start", formatter.format(firstTimestamp),
                        "end", formatter.format(lastTimestamp),
                        "durationMillis", duration.toMillis()
                ));
            }

            return summary;

        } catch (IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public List<Map<String, Object>> getTopMessages(int limit) {
        if (lastUploadedFilePath == null) {
            return Collections.emptyList();
        }
        try {
            List<String> lines = Files.readAllLines(lastUploadedFilePath, StandardCharsets.UTF_8);
            Map<String, Long> messageCounts = lines.stream()
                    .map(this::parseLogLine)
                    .filter(Objects::nonNull)
                    .map(logEntry -> logEntry.get("message"))  // Извлекаем только поле "message"
                    .filter(message -> message != null && !message.isBlank())
                    .collect(Collectors.groupingBy(message -> message, Collectors.counting()));

            return messageCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(limit)
                    .map(entry -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("message", entry.getKey());
                        map.put("count", entry.getValue());
                        return map;
                    })
                    .collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public List<Map<String, String>> searchLogs(String query, boolean caseSensitive, boolean regex, int page, int size) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query parameter is required.");
        }

        if (page <= 0 || size <= 0) {
            throw new IllegalArgumentException("Page and size must be greater than 0.");
        }

        if (lastUploadedFilePath == null) {
            throw new IllegalStateException("No file has been uploaded yet.");
        }

        try (BufferedReader reader = Files.newBufferedReader(lastUploadedFilePath, StandardCharsets.UTF_8)) {
            List<Map<String, String>> matchingEntries = new ArrayList<>();
            Pattern pattern;

            // Создаем паттерн с учетом регистра
            if (regex) {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                pattern = Pattern.compile(query, flags);
            } else {
                String processedQuery = caseSensitive ? query : query.toLowerCase(Locale.ROOT);
                pattern = Pattern.compile(Pattern.quote(processedQuery), caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                Map<String, String> logEntry = parseLogLine(line);
                if (logEntry != null) {
                    String message = logEntry.get("message");
                    if (message != null && pattern.matcher(message).find()) {
                        matchingEntries.add(logEntry);
                    }
                }
            }

            // Пагинация
            int startIndex = (page - 1) * size;
            int endIndex = Math.min(startIndex + size, matchingEntries.size());

            if (startIndex >= matchingEntries.size()) {
                return Collections.emptyList(); // Пустой результат если запрашивается страница за пределами списка
            }

            return matchingEntries.subList(startIndex, endIndex);

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to read or parse log file.");
        }
    }
}
