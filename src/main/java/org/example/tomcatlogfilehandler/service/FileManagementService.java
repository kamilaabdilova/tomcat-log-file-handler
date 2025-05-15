package org.example.tomcatlogfilehandler.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileManagementService {
    private static final Path CONFIG_FILE = Paths.get("./logs/last_uploaded_file.txt");

    public Path getLastUploadedFilePath() throws IOException {
        if (Files.exists(CONFIG_FILE)) {
            String filePath = Files.readString(CONFIG_FILE).trim();
            if (!filePath.isBlank()) {
                Path path = Paths.get(filePath);
                if (Files.exists(path)) {
                    return path;
                }
            }
        }
        return null;
    }

    public void saveLastUploadedFilePath(Path filePath) throws IOException {
        Files.writeString(CONFIG_FILE, filePath.toString());
        System.out.println("Last uploaded file path saved: " + filePath);
    }
}
