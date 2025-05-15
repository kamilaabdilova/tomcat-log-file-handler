package org.example.tomcatlogfilehandler.controller;

import org.example.tomcatlogfilehandler.service.TomcatLogFileHandlerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class TomcatLogFileHandlerController {

    private final TomcatLogFileHandlerService tomcatLogFileHandlerService;

    @Value("${log.directory}")
    private String logDirectory;

    @Autowired
    public TomcatLogFileHandlerController(TomcatLogFileHandlerService tomcatLogFileHandlerService) {
        this.tomcatLogFileHandlerService = tomcatLogFileHandlerService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadLogFile(@RequestParam("file") MultipartFile file) {
        try {
            String fileName = tomcatLogFileHandlerService.uploadLogFile(file, logDirectory);
            return ResponseEntity.ok("{\"status\": \"success\", \"message\": \"File '" + fileName + "' uploaded and saved.\"}");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"status\": \"error\", \"message\": \"Failed to save file.\"}");
        }
    }

    @GetMapping("/analysis/summary")
    public ResponseEntity<Map<String, Object>> getLogSummary() {
        Map<String, Object> summary = tomcatLogFileHandlerService.getLogSummary();
        return summary.isEmpty()
                ? ResponseEntity.status(HttpStatus.NO_CONTENT).body(null)
                : ResponseEntity.ok(summary);
    }

    @GetMapping("/analysis/top-messages")
    public ResponseEntity<?> getTopMessages(@RequestParam(name = "limit", required = false) Integer limit) {
        if (limit != null && limit <= 0) {
            return ResponseEntity.badRequest().body("{\"status\": \"error\", \"message\": \"Limit must be greater than 0.\"}");
        }

        List<Map<String, Object>> topMessages = tomcatLogFileHandlerService.getTopMessages(limit);
        return topMessages.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(topMessages);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchLogs(
            @RequestParam("query") String query,
            @RequestParam(name = "caseSensitive", defaultValue = "false") boolean caseSensitive,
            @RequestParam(name = "regex", defaultValue = "false") boolean regex,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        try {
            List<Map<String, String>> matchingLogs = tomcatLogFileHandlerService.searchLogs(query, caseSensitive, regex, page, size);
            return matchingLogs.isEmpty()
                    ? ResponseEntity.noContent().build()
                    : ResponseEntity.ok(matchingLogs);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"status\": \"error\", \"message\": \"No file has been uploaded yet.\"}");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"status\": \"error\", \"message\": \"Internal server error.\"}");
        }
    }
}
