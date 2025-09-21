package com.example.licenseplate.controller;

import com.example.licenseplate.dto.ProcessingResult;
import com.example.licenseplate.service.ImageProcessorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/license-plate")
@CrossOrigin(origins = "*")
public class LicensePlateController {

    @Autowired
    private ImageProcessorService imageProcessorService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        System.out.println("[HEALTH] Health check requisitado");

        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("service", "License Plate Detection API");
        health.put("version", "1.0.0");

        return ResponseEntity.ok(health);
    }

    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> processImage(@RequestParam("image") MultipartFile file) {
        String processId = UUID.randomUUID().toString();
        System.out.println("[CONTROLLER] POST /process - ProcessId: " + processId);
        System.out.println("[CONTROLLER] Arquivo: " + file.getOriginalFilename() + " (" + file.getSize() + " bytes)");

        try {
            // Validações básicas
            if (file.isEmpty()) {
                System.out.println("[CONTROLLER] Erro: Arquivo vazio");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Arquivo de imagem não fornecido"));
            }

            String contentType = file.getContentType();
            System.out.println("[CONTROLLER] Content-Type: " + contentType);

            if (contentType == null || !isValidImageType(contentType)) {
                System.out.println("[CONTROLLER] Erro: Tipo inválido - " + contentType);
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Tipo de arquivo inválido. Aceitos: JPEG, PNG, BMP"));
            }

            if (file.getSize() > 10 * 1024 * 1024) {
                System.out.println("[CONTROLLER] Erro: Arquivo muito grande - " + file.getSize());
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Arquivo muito grande. Tamanho máximo: 10MB"));
            }

            System.out.println("[CONTROLLER] Iniciando processamento assíncrono...");

            // Iniciar processamento - sem await, é assíncrono
            imageProcessorService.processImageAsync(processId, file.getBytes())
                    .thenAccept(result -> {
                        System.out.println("[CONTROLLER] Processamento concluído para " + processId + " - Status: " + result.getStatus());
                    })
                    .exceptionally(throwable -> {
                        System.err.println("[CONTROLLER] Erro no processamento " + processId + ": " + throwable.getMessage());
                        return null;
                    });

            Map<String, String> response = new HashMap<>();
            response.put("processId", processId);
            response.put("status", "PROCESSING");
            response.put("message", "Processamento iniciado. Use o processId para verificar o status.");

            System.out.println("[CONTROLLER] Retornando response imediato para " + processId);
            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            System.err.println("[CONTROLLER] Erro no controller: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erro interno: " + e.getMessage()));
        }
    }

    @GetMapping("/status/{processId}")
    public ResponseEntity<ProcessingResult> getProcessingStatus(@PathVariable String processId) {
        System.out.println("[CONTROLLER] GET /status/" + processId + " requisitado");

        try {
            ProcessingResult result = imageProcessorService.getProcessingStatus(processId);

            if (result == null) {
                System.out.println("[CONTROLLER] ProcessId não encontrado: " + processId);
                return ResponseEntity.notFound().build();
            }

            System.out.println("[CONTROLLER] Status encontrado para " + processId + ": " + result.getStatus());

            // Log detalhado do resultado
            if ("COMPLETED".equals(result.getStatus())) {
                System.out.println("[CONTROLLER] Detalhes: placa=" + result.getLicensePlate() +
                        ", formato=" + result.getPlateFormat() +
                        ", tempo=" + result.getProcessingTimeMs() + "ms");
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("[CONTROLLER] Erro ao consultar status: " + e.getMessage());
            e.printStackTrace();

            ProcessingResult errorResult = new ProcessingResult("ERROR");
            errorResult.setMessage("Erro ao consultar status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }

    @DeleteMapping("/clear/{processId}")
    public ResponseEntity<Map<String, String>> clearProcessingResult(@PathVariable String processId) {
        System.out.println("[CONTROLLER] DELETE /clear/" + processId + " requisitado");

        try {
            imageProcessorService.clearProcessingResult(processId);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Resultado removido com sucesso");
            response.put("processId", processId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("[CONTROLLER] Erro ao limpar resultado: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erro ao limpar resultado: " + e.getMessage()));
        }
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> testEndpoint() {
        System.out.println("[CONTROLLER] GET /test requisitado");

        Map<String, String> response = new HashMap<>();
        response.put("message", "API funcionando!");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("tesseract_path", System.getProperty("tesseract.data.path", "não configurado"));

        return ResponseEntity.ok(response);
    }

    // Endpoint para debug do cache
    @GetMapping("/debug/cache")
    public ResponseEntity<Map<String, Object>> debugCache() {
        Map<String, Object> debug = new HashMap<>();
        debug.put("message", "Cache debug info");
        debug.put("cacheSize", imageProcessorService.getCacheSize()); // Vamos adicionar este método
        debug.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(debug);
    }

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