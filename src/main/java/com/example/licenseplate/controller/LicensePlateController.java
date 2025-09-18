package com.example.licenseplate.controller;

import com.example.licenseplate.dto.ProcessingResult;
import com.example.licenseplate.service.ImageProcessorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/license-plate")
@CrossOrigin(origins = "*")
public class LicensePlateController {

    @Autowired
    private ImageProcessorService imageProcessorService;

    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> processImage(@RequestParam("image") MultipartFile file) {
        try {
            // Validar arquivo
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Arquivo de imagem não fornecido"));
            }

            // Validar tipo de arquivo
            String contentType = file.getContentType();
            if (contentType == null || !isValidImageType(contentType)) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Tipo de arquivo inválido. Aceitos: JPEG, PNG, BMP"));
            }

            // Validar tamanho do arquivo (máximo 10MB)
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Arquivo muito grande. Tamanho máximo: 10MB"));
            }

            // Gerar ID único para o processamento
            String processId = UUID.randomUUID().toString();

            // Iniciar processamento assíncrono
            CompletableFuture<ProcessingResult> processingFuture =
                    imageProcessorService.processImageAsync(processId, file.getBytes());

            // Retornar ID do processo imediatamente
            Map<String, String> response = new HashMap<>();
            response.put("processId", processId);
            response.put("status", "PROCESSING");
            response.put("message", "Processamento iniciado. Use o processId para verificar o status.");

            return ResponseEntity.accepted().body(response);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erro ao processar arquivo: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erro interno: " + e.getMessage()));
        }
    }

    @GetMapping("/status/{processId}")
    public ResponseEntity<ProcessingResult> getProcessingStatus(@PathVariable String processId) {
        try {
            ProcessingResult result = imageProcessorService.getProcessingStatus(processId);

            if (result == null) {
                ProcessingResult notFound = new ProcessingResult("NOT_FOUND");
                notFound.setMessage("Process ID não encontrado");
                return ResponseEntity.notFound().build();
            }

            // Se completado, limpar do cache após retornar (opcional)
            if ("COMPLETED".equals(result.getStatus()) || "ERROR".equals(result.getStatus())) {
                // Opcional: limpar após 1 minuto para permitir múltiplas consultas
                // imageProcessorService.clearProcessingResult(processId);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            ProcessingResult errorResult = new ProcessingResult("ERROR");
            errorResult.setMessage("Erro ao consultar status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }

    @DeleteMapping("/clear/{processId}")
    public ResponseEntity<Map<String, String>> clearProcessingResult(@PathVariable String processId) {
        try {
            imageProcessorService.clearProcessingResult(processId);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Resultado do processamento removido com sucesso");
            response.put("processId", processId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erro ao limpar resultado: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("service", "License Plate Detection API");
        health.put("version", "1.0.0");

        return ResponseEntity.ok(health);
    }

    // Métodos auxiliares
    private boolean isValidImageType(String contentType) {
        return contentType.equals("image/jpeg") ||
                contentType.equals("image/jpg") ||
                contentType.equals("image/png") ||
                contentType.equals("image/bmp");
    }

    private Map<String, String> createErrorResponse(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        error.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return error;
    }
}