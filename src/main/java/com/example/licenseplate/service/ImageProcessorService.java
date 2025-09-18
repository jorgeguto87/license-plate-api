package com.example.licenseplate.service;

import com.example.licenseplate.dto.ProcessingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImageProcessorService {

    @Autowired
    private LicensePlateDetector plateDetector;

    @Value("${image.compression.quality:0.8}")
    private double compressionQuality;

    @Value("${image.max.width:1920}")
    private int maxWidth;

    @Value("${image.max.height:1080}")
    private int maxHeight;

    private final ConcurrentHashMap<String, ProcessingResult> processingCache = new ConcurrentHashMap<>();

    @Async
    public CompletableFuture<ProcessingResult> processImageAsync(String processId, byte[] imageData) {
        long startTime = System.currentTimeMillis();

        try {
            // Atualizar status para processando
            processingCache.put(processId, ProcessingResult.processing(processId));

            // Detectar placa
            LicensePlateDetector.PlateDetectionResult detection = plateDetector.detectPlate(imageData);

            // Processar imagem
            byte[] processedImage;
            ProcessingResult result;

            if (detection.isFound()) {
                // Aplicar blur na placa e comprimir
                processedImage = blurPlateAndCompress(imageData, detection.getCoordinates());

                ProcessingResult.PlateCoordinates coords = new ProcessingResult.PlateCoordinates(
                        detection.getCoordinates().x,
                        detection.getCoordinates().y,
                        detection.getCoordinates().width,
                        detection.getCoordinates().height
                );

                result = ProcessingResult.completedWithPlate(
                        processId,
                        detection.getPlateText(),
                        detection.getFormat(),
                        coords,
                        Base64.getEncoder().encodeToString(processedImage)
                );
            } else {
                // Apenas comprimir a imagem
                processedImage = compressImage(imageData);
                result = ProcessingResult.completed(
                        processId,
                        Base64.getEncoder().encodeToString(processedImage)
                );
            }

            long processingTime = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(processingTime);

            // Armazenar resultado
            processingCache.put(processId, result);

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            ProcessingResult errorResult = ProcessingResult.error(processId, e.getMessage());
            processingCache.put(processId, errorResult);
            return CompletableFuture.completedFuture(errorResult);
        }
    }

    public ProcessingResult getProcessingStatus(String processId) {
        return processingCache.get(processId);
    }

    public void clearProcessingResult(String processId) {
        processingCache.remove(processId);
    }

    private byte[] blurPlateAndCompress(byte[] imageData, Rectangle plateRegion) throws IOException {
        // Converter byte array para BufferedImage
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));

        if (image == null) {
            throw new IOException("Não foi possível decodificar a imagem");
        }

        // Redimensionar se necessário
        BufferedImage resizedImage = resizeImageIfNeeded(image);

        // Ajustar coordenadas da placa para a nova escala
        Rectangle adjustedPlateRegion = adjustRectForResize(plateRegion, image, resizedImage);

        // Garantir que a região está dentro dos limites da imagem
        adjustedPlateRegion = constrainRectToBounds(adjustedPlateRegion, resizedImage);

        // Aplicar blur gaussiano na região da placa
        if (adjustedPlateRegion.width > 0 && adjustedPlateRegion.height > 0) {
            applyGaussianBlur(resizedImage, adjustedPlateRegion);
        }

        // Comprimir e retornar
        return compressBufferedImage(resizedImage);
    }

    private byte[] compressImage(byte[] imageData) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));

        if (image == null) {
            throw new IOException("Não foi possível decodificar a imagem");
        }

        BufferedImage resizedImage = resizeImageIfNeeded(image);
        return compressBufferedImage(resizedImage);
    }

    private BufferedImage resizeImageIfNeeded(BufferedImage image) {
        int currentWidth = image.getWidth();
        int currentHeight = image.getHeight();

        if (currentWidth <= maxWidth && currentHeight <= maxHeight) {
            return image; // Não precisa redimensionar
        }

        // Calcular nova escala mantendo proporção
        double scaleX = (double) maxWidth / currentWidth;
        double scaleY = (double) maxHeight / currentHeight;
        double scale = Math.min(scaleX, scaleY);

        int newWidth = (int) (currentWidth * scale);
        int newHeight = (int) (currentHeight * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(image, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return resized;
    }

    private Rectangle adjustRectForResize(Rectangle originalRect, BufferedImage originalImage, BufferedImage newImage) {
        double scaleX = (double) newImage.getWidth() / originalImage.getWidth();
        double scaleY = (double) newImage.getHeight() / originalImage.getHeight();

        int newX = (int) (originalRect.x * scaleX);
        int newY = (int) (originalRect.y * scaleY);
        int newWidth = (int) (originalRect.width * scaleX);
        int newHeight = (int) (originalRect.height * scaleY);

        return new Rectangle(newX, newY, newWidth, newHeight);
    }

    private Rectangle constrainRectToBounds(Rectangle rect, BufferedImage image) {
        int x = Math.max(0, Math.min(rect.x, image.getWidth() - 1));
        int y = Math.max(0, Math.min(rect.y, image.getHeight() - 1));
        int width = Math.min(rect.width, image.getWidth() - x);
        int height = Math.min(rect.height, image.getHeight() - y);

        return new Rectangle(x, y, Math.max(1, width), Math.max(1, height));
    }

    private void applyGaussianBlur(BufferedImage image, Rectangle region) {
        try {
            // Implementação simples de blur usando convolução
            int blurRadius = 15;

            // Criar uma cópia da região para aplicar o blur
            BufferedImage regionCopy = new BufferedImage(region.width, region.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = regionCopy.createGraphics();
            g2d.drawImage(image, 0, 0, region.width, region.height,
                    region.x, region.y, region.x + region.width, region.y + region.height, null);
            g2d.dispose();

            // Aplicar blur simples (box blur aproximando gaussian)
            BufferedImage blurred = applyBoxBlur(regionCopy, blurRadius);

            // Copiar a região blurrada de volta para a imagem original
            Graphics2D originalG2d = image.createGraphics();
            originalG2d.drawImage(blurred, region.x, region.y, null);
            originalG2d.dispose();

        } catch (Exception e) {
            System.err.println("Erro ao aplicar blur: " + e.getMessage());
        }
    }

    private BufferedImage applyBoxBlur(BufferedImage image, int radius) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = 0, green = 0, blue = 0, count = 0;

                // Calcular a média dos pixels na região do blur
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int px = x + dx;
                        int py = y + dy;

                        if (px >= 0 && px < width && py >= 0 && py < height) {
                            Color color = new Color(image.getRGB(px, py));
                            red += color.getRed();
                            green += color.getGreen();
                            blue += color.getBlue();
                            count++;
                        }
                    }
                }

                if (count > 0) {
                    red /= count;
                    green /= count;
                    blue /= count;
                    result.setRGB(x, y, new Color(red, green, blue).getRGB());
                }
            }
        }

        return result;
    }

    private byte[] compressBufferedImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Usar ImageWriter para controle de qualidade
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("Nenhum writer JPEG encontrado");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality((float) compressionQuality);
        }

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
            writer.dispose();
        }

        return baos.toByteArray();
    }
}