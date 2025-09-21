package com.example.licenseplate.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Service
public class ImageSaveService {

    @Value("${image.save.path:./processed-images}")
    private String baseSavePath;

    @Value("${image.save.enabled:false}")
    private boolean saveEnabled;

    @PostConstruct
    public void init() {
        System.out.println("[SAVE-SERVICE] Configuração carregada: enabled=" + saveEnabled + ", path=" + baseSavePath);
    }

    public String saveProcessedImage(String processId, String base64Image, String plateText) {
        if (!saveEnabled || base64Image == null || base64Image.isEmpty()) {
            return null;
        }

        try {
            Path saveDir = Paths.get(baseSavePath);
            if (!Files.exists(saveDir)) {
                Files.createDirectories(saveDir);
                System.out.println("[SAVE-SERVICE] Diretório criado: " + saveDir.toAbsolutePath());
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String plateInfo = plateText != null ? "_" + plateText : "_no_plate";
            String filename = String.format("processed_%s%s_%s.jpg", timestamp, plateInfo, processId.substring(0, 8));

            Path filePath = saveDir.resolve(filename);

            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            Files.write(filePath, imageBytes);

            String savedPath = filePath.toAbsolutePath().toString();
            System.out.println("[SAVE-SERVICE] Imagem salva: " + savedPath);

            return savedPath;

        } catch (Exception e) {
            System.err.println("[SAVE-SERVICE] Erro ao salvar imagem: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String saveOriginalImage(String processId, byte[] imageData) {
        if (!saveEnabled || imageData == null || imageData.length == 0) {
            return null;
        }

        try {
            Path saveDir = Paths.get(baseSavePath);
            if (!Files.exists(saveDir)) {
                Files.createDirectories(saveDir);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("original_%s_%s.jpg", timestamp, processId.substring(0, 8));
            Path filePath = saveDir.resolve(filename);

            Files.write(filePath, imageData);

            String savedPath = filePath.toAbsolutePath().toString();
            System.out.println("[SAVE-SERVICE] Imagem original salva: " + savedPath);

            return savedPath;

        } catch (Exception e) {
            System.err.println("[SAVE-SERVICE] Erro ao salvar imagem original: " + e.getMessage());
            return null;
        }
    }

    public int cleanOldImages(int daysOld) {
        if (!saveEnabled || daysOld <= 0) {
            return 0;
        }

        try {
            Path saveDir = Paths.get(baseSavePath);
            if (!Files.exists(saveDir)) {
                return 0;
            }

            long cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L);
            int removedCount = 0;

            Files.list(saveDir)
                    .filter(path -> path.toString().endsWith(".jpg") || path.toString().endsWith(".jpeg"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            System.out.println("[SAVE-SERVICE] Arquivo removido: " + path.getFileName());
                        } catch (IOException e) {
                            System.err.println("[SAVE-SERVICE] Erro ao remover: " + path.getFileName());
                        }
                    });

            return removedCount;

        } catch (Exception e) {
            System.err.println("[SAVE-SERVICE] Erro na limpeza: " + e.getMessage());
            return 0;
        }
    }

    public boolean isSaveEnabled() {
        return saveEnabled;
    }

    public String getSavePath() {
        return baseSavePath;
    }
}
