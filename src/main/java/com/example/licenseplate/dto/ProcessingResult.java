package com.example.licenseplate.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessingResult {

    private String status;
    private String processId;
    private String licensePlate;
    private String plateFormat;
    private PlateCoordinates coordinates;
    private String processedImageBase64;
    private String message;
    private Long processingTimeMs;

    public ProcessingResult() {}

    public ProcessingResult(String status) {
        this.status = status;
    }

    public ProcessingResult(String status, String processId) {
        this.status = status;
        this.processId = processId;
    }

    // Static factory methods
    public static ProcessingResult processing(String processId) {
        return new ProcessingResult("PROCESSING", processId);
    }

    public static ProcessingResult completed(String processId, String processedImageBase64) {
        ProcessingResult result = new ProcessingResult("COMPLETED", processId);
        result.processedImageBase64 = processedImageBase64;
        return result;
    }

    public static ProcessingResult completedWithPlate(String processId, String licensePlate,
                                                      String plateFormat, PlateCoordinates coordinates,
                                                      String processedImageBase64) {
        ProcessingResult result = new ProcessingResult("COMPLETED", processId);
        result.licensePlate = licensePlate;
        result.plateFormat = plateFormat;
        result.coordinates = coordinates;
        result.processedImageBase64 = processedImageBase64;
        return result;
    }

    public static ProcessingResult error(String processId, String message) {
        ProcessingResult result = new ProcessingResult("ERROR", processId);
        result.message = message;
        return result;
    }

    // Getters and Setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProcessId() { return processId; }
    public void setProcessId(String processId) { this.processId = processId; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public String getPlateFormat() { return plateFormat; }
    public void setPlateFormat(String plateFormat) { this.plateFormat = plateFormat; }

    public PlateCoordinates getCoordinates() { return coordinates; }
    public void setCoordinates(PlateCoordinates coordinates) { this.coordinates = coordinates; }

    public String getProcessedImageBase64() { return processedImageBase64; }
    public void setProcessedImageBase64(String processedImageBase64) { this.processedImageBase64 = processedImageBase64; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }

    public static class PlateCoordinates {
        private int x, y, width, height;

        public PlateCoordinates() {}

        public PlateCoordinates(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        // Getters and Setters
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }

        public int getY() { return y; }
        public void setY(int y) { this.y = y; }

        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }

        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
    }
}