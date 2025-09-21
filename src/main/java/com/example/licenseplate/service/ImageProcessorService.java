package com.example.licenseplate.service;

import com.example.licenseplate.service.ImageSaveService;
import com.example.licenseplate.dto.ProcessingResult;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private ImageSaveService imageSaveService;

    private final ConcurrentHashMap<String, ProcessingResult> processingCache = new ConcurrentHashMap<>();

    // Configurações otimizadas para processamento
    private static final float COMPRESSION_QUALITY = 0.85f;
    private static final int BLUR_INTENSITY = 12;
    private static final int PIXELATION_SIZE = 8;

    // Timeout para processamento (30 segundos)
    private static final long PROCESSING_TIMEOUT_MS = 30000;

    @Async
    public CompletableFuture<ProcessingResult> processImageAsync(String processId, byte[] imageData) {
        long startTime = System.currentTimeMillis();
        System.out.println("[IMAGE-PROCESSOR] Iniciando processamento inteligente para ID: " + processId);
        System.out.println("[IMAGE-PROCESSOR] Tamanho da imagem: " + imageData.length + " bytes");

        try {
            // Status inicial PROCESSING no cache
            ProcessingResult processingStatus = ProcessingResult.processing(processId);
            processingCache.put(processId, processingStatus);
            System.out.println("[IMAGE-PROCESSOR] Status PROCESSING salvo no cache para " + processId);

            // Validação e carregamento da imagem
            BufferedImage originalImage = validateAndLoadImage(imageData);
            if (originalImage == null) {
                ProcessingResult errorResult = ProcessingResult.error(processId, "Imagem inválida ou corrompida");
                processingCache.put(processId, errorResult);
                return CompletableFuture.completedFuture(errorResult);
            }

            System.out.println("[IMAGE-PROCESSOR] Imagem válida: " + originalImage.getWidth() + "x" + originalImage.getHeight());

            // Salvar imagem original se habilitado
            saveOriginalImageIfEnabled(processId, imageData);

            // Detecção inteligente de placa com timeout
            System.out.println("[IMAGE-PROCESSOR] Iniciando detecção inteligente...");
            LicensePlateDetector.PlateDetectionResult detection = performDetectionWithTimeout(imageData, startTime);

            ProcessingResult result = processDetectionResult(processId, originalImage, detection);

            long processingTime = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(processingTime);

            // Salvar resultado final no cache
            processingCache.put(processId, result);
            System.out.println("[IMAGE-PROCESSOR] Processamento concluído em " + processingTime + "ms - Status: " + result.getStatus());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            System.err.println("[IMAGE-PROCESSOR] ERRO no processamento " + processId + ": " + e.getMessage());
            e.printStackTrace();

            ProcessingResult errorResult = ProcessingResult.error(processId, "Erro interno: " + e.getMessage());
            processingCache.put(processId, errorResult);
            return CompletableFuture.completedFuture(errorResult);
        }
    }

    private LicensePlateDetector.PlateDetectionResult performDetectionWithTimeout(byte[] imageData, long startTime) {
        try {
            // Verificar timeout antes de iniciar
            if (System.currentTimeMillis() - startTime > PROCESSING_TIMEOUT_MS) {
                System.err.println("[IMAGE-PROCESSOR] Timeout antes da detecção");
                return new LicensePlateDetector.PlateDetectionResult(false, null, null, null);
            }

            return plateDetector.detectPlate(imageData);

        } catch (Exception e) {
            System.err.println("[IMAGE-PROCESSOR] Erro na detecção: " + e.getMessage());
            return new LicensePlateDetector.PlateDetectionResult(false, null, null, null);
        }
    }

    private BufferedImage validateAndLoadImage(byte[] imageData) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                System.err.println("[IMAGE-PROCESSOR] Erro: Imagem não pôde ser decodificada");
                return null;
            }

            // Validações básicas
            if (image.getWidth() < 100 || image.getHeight() < 100) {
                System.err.println("[IMAGE-PROCESSOR] Erro: Imagem muito pequena (" +
                        image.getWidth() + "x" + image.getHeight() + ")");
                return null;
            }

            if (image.getWidth() > 4000 || image.getHeight() > 4000) {
                System.out.println("[IMAGE-PROCESSOR] Imagem muito grande, redimensionando...");
                return resizeImageIntelligent(image, 2000, 2000);
            }

            return image;

        } catch (Exception e) {
            System.err.println("[IMAGE-PROCESSOR] Erro ao validar imagem: " + e.getMessage());
            return null;
        }
    }

    private BufferedImage resizeImageIntelligent(BufferedImage image, int maxWidth, int maxHeight) {
        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();

        // Calcular nova dimensão mantendo proporção
        double scaleW = (double) maxWidth / originalWidth;
        double scaleH = (double) maxHeight / originalHeight;
        double scale = Math.min(scaleW, scaleH);

        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();

        // Configurações de alta qualidade
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(image, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        System.out.println("[IMAGE-PROCESSOR] Imagem redimensionada de " + originalWidth + "x" + originalHeight +
                " para " + newWidth + "x" + newHeight);

        return resized;
    }

    private void saveOriginalImageIfEnabled(String processId, byte[] imageData) {
        try {
            if (imageSaveService != null && imageSaveService.isSaveEnabled()) {
                String originalPath = imageSaveService.saveOriginalImage(processId, imageData);
                if (originalPath != null) {
                    System.out.println("[IMAGE-PROCESSOR] Imagem original salva: " + originalPath);
                }
            }
        } catch (Exception e) {
            System.err.println("[IMAGE-PROCESSOR] Erro ao salvar imagem original: " + e.getMessage());
        }
    }

    private ProcessingResult processDetectionResult(String processId, BufferedImage originalImage,
                                                    LicensePlateDetector.PlateDetectionResult detection) throws IOException {

        if (detection.isFound()) {
            return processWithPlateDetected(processId, originalImage, detection);
        } else {
            return processWithoutPlate(processId, originalImage);
        }
    }

    private ProcessingResult processWithPlateDetected(String processId, BufferedImage originalImage,
                                                      LicensePlateDetector.PlateDetectionResult detection) throws IOException {

        System.out.println("[IMAGE-PROCESSOR] Placa detectada: " + detection.getPlateText() + " (" + detection.getFormat() + ")");
        System.out.println("[IMAGE-PROCESSOR] Coordenadas: " + detection.getCoordinates());

        // Aplicar blur inteligente e comprimir
        byte[] processedImageBytes = applyIntelligentBlurAndCompress(originalImage, detection.getCoordinates());
        String base64Image = Base64.getEncoder().encodeToString(processedImageBytes);

        System.out.println("[IMAGE-PROCESSOR] Blur aplicado. Tamanho final: " + processedImageBytes.length + " bytes");

        // Salvar imagem processada
        saveProcessedImageIfEnabled(processId, base64Image, detection.getPlateText());

        // Criar coordenadas do resultado
        ProcessingResult.PlateCoordinates coords = new ProcessingResult.PlateCoordinates(
                detection.getCoordinates().x,
                detection.getCoordinates().y,
                detection.getCoordinates().width,
                detection.getCoordinates().height
        );

        return ProcessingResult.completedWithPlate(
                processId,
                detection.getPlateText(),
                detection.getFormat(),
                coords,
                base64Image
        );
    }

    private ProcessingResult processWithoutPlate(String processId, BufferedImage originalImage) throws IOException {
        System.out.println("[IMAGE-PROCESSOR] Nenhuma placa detectada");

        // Apenas comprimir a imagem
        byte[] compressedBytes = compressImageOptimized(originalImage);
        String base64Image = Base64.getEncoder().encodeToString(compressedBytes);

        System.out.println("[IMAGE-PROCESSOR] Imagem comprimida. Tamanho: " + compressedBytes.length + " bytes");

        // Salvar imagem comprimida
        saveProcessedImageIfEnabled(processId, base64Image, null);

        return ProcessingResult.completed(processId, base64Image);
    }

    private void saveProcessedImageIfEnabled(String processId, String base64Image, String plateText) {
        if (imageSaveService != null && imageSaveService.isSaveEnabled()) {
            try {
                String processedPath = imageSaveService.saveProcessedImage(processId, base64Image, plateText);
                if (processedPath != null) {
                    System.out.println("[IMAGE-PROCESSOR] Imagem processada salva em: " + processedPath);
                }
            } catch (Exception e) {
                System.err.println("[IMAGE-PROCESSOR] Erro ao salvar imagem processada: " + e.getMessage());
            }
        }
    }

    private byte[] applyIntelligentBlurAndCompress(BufferedImage image, Rectangle plateRegion) throws IOException {
        System.out.println("[IMAGE-PROCESSOR] Aplicando blur inteligente na região: " + plateRegion);

        // Validar e ajustar região da placa
        Rectangle adjustedRegion = validateAndAdjustPlateRegion(plateRegion, image.getWidth(), image.getHeight());
        System.out.println("[IMAGE-PROCESSOR] Região ajustada: " + adjustedRegion);

        // Criar cópia da imagem para processamento
        BufferedImage processedImage = createImageCopy(image);

        // Aplicar blur sofisticado
        applyAdvancedBlur(processedImage, adjustedRegion);

        // Adicionar indicador visual
        addPrivacyIndicator(processedImage, adjustedRegion);

        return compressImageOptimized(processedImage);
    }

    private Rectangle validateAndAdjustPlateRegion(Rectangle plateRegion, int imageWidth, int imageHeight) {
        if (plateRegion == null || isInvalidRegion(plateRegion, imageWidth, imageHeight)) {
            System.out.println("[IMAGE-PROCESSOR] Região inválida, criando estimativa inteligente");
            return createIntelligentPlateEstimate(imageWidth, imageHeight);
        }

        // Expandir região ligeiramente para garantir cobertura completa
        return expandRegionSafely(plateRegion, imageWidth, imageHeight);
    }

    private boolean isInvalidRegion(Rectangle region, int imageWidth, int imageHeight) {
        return region.x < 0 || region.y < 0 ||
                region.width <= 0 || region.height <= 0 ||
                region.x + region.width > imageWidth ||
                region.y + region.height > imageHeight ||
                region.width > imageWidth * 0.8 ||
                region.height > imageHeight * 0.4;
    }

    private Rectangle createIntelligentPlateEstimate(int imageWidth, int imageHeight) {
        // Estimativa baseada em padrões típicos de placas em fotos
        int estimatedWidth = Math.min(imageWidth / 3, Math.max(150, imageWidth / 4));
        int estimatedHeight = Math.max(50, estimatedWidth / 3); // Proporção típica de placa

        // Posicionar na região inferior-central (onde placas geralmente aparecem)
        int estimatedX = (imageWidth - estimatedWidth) / 2;
        int estimatedY = (int) (imageHeight * 0.75);

        // Ajustar se sair dos limites
        if (estimatedY + estimatedHeight > imageHeight) {
            estimatedY = Math.max(0, imageHeight - estimatedHeight - 20);
        }

        Rectangle estimate = new Rectangle(estimatedX, estimatedY, estimatedWidth, estimatedHeight);
        System.out.println("[IMAGE-PROCESSOR] Estimativa criada: " + estimate);

        return estimate;
    }

    private Rectangle expandRegionSafely(Rectangle region, int imageWidth, int imageHeight) {
        // Expansão de 15% para garantir cobertura total da placa (aumentado de 10% para 15%)
        int expandX = Math.max(8, region.width / 7); // Aumentado
        int expandY = Math.max(5, region.height / 6); // Aumentado

        int newX = Math.max(0, region.x - expandX);
        int newY = Math.max(0, region.y - expandY);
        int newWidth = Math.min(imageWidth - newX, region.width + (expandX * 2));
        int newHeight = Math.min(imageHeight - newY, region.height + (expandY * 2));

        return new Rectangle(newX, newY, newWidth, newHeight);
    }

    private BufferedImage createImageCopy(BufferedImage original) {
        BufferedImage copy = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = copy.createGraphics();
        g2d.drawImage(original, 0, 0, null);
        g2d.dispose();
        return copy;
    }

    private void applyAdvancedBlur(BufferedImage image, Rectangle region) {
        System.out.println("[IMAGE-PROCESSOR] Aplicando blur avançado...");

        // Blur gaussiano suave seguido de pixelização
        applyGaussianBlur(image, region);
        applyPixelationEffect(image, region);
    }

    private void applyGaussianBlur(BufferedImage image, Rectangle region) {
        // Implementação simples de blur gaussiano
        int radius = Math.max(4, Math.min(region.width / 18, region.height / 7)); // Aumentado ligeiramente

        // Criar kernel gaussiano
        float[] kernel = createGaussianKernel(radius);

        // Aplicar blur horizontal e vertical
        blurRegionWithKernel(image, region, kernel, radius, true);  // Horizontal
        blurRegionWithKernel(image, region, kernel, radius, false); // Vertical
    }

    private float[] createGaussianKernel(int radius) {
        int size = radius * 2 + 1;
        float[] kernel = new float[size];
        float sigma = radius / 3.0f;
        float sum = 0;

        for (int i = 0; i < size; i++) {
            int x = i - radius;
            kernel[i] = (float) Math.exp(-(x * x) / (2 * sigma * sigma));
            sum += kernel[i];
        }

        // Normalizar kernel
        for (int i = 0; i < size; i++) {
            kernel[i] /= sum;
        }

        return kernel;
    }

    private void blurRegionWithKernel(BufferedImage image, Rectangle region, float[] kernel, int radius, boolean horizontal) {
        BufferedImage temp = createImageCopy(image);

        for (int y = region.y; y < region.y + region.height && y < image.getHeight(); y++) {
            for (int x = region.x; x < region.x + region.width && x < image.getWidth(); x++) {

                float red = 0, green = 0, blue = 0;

                for (int i = 0; i < kernel.length; i++) {
                    int offset = i - radius;
                    int sampleX = horizontal ? Math.max(region.x, Math.min(region.x + region.width - 1, x + offset)) : x;
                    int sampleY = horizontal ? y : Math.max(region.y, Math.min(region.y + region.height - 1, y + offset));

                    if (sampleX >= 0 && sampleX < temp.getWidth() && sampleY >= 0 && sampleY < temp.getHeight()) {
                        Color sampleColor = new Color(temp.getRGB(sampleX, sampleY));
                        red += sampleColor.getRed() * kernel[i];
                        green += sampleColor.getGreen() * kernel[i];
                        blue += sampleColor.getBlue() * kernel[i];
                    }
                }

                Color blurredColor = new Color(
                        Math.max(0, Math.min(255, (int) red)),
                        Math.max(0, Math.min(255, (int) green)),
                        Math.max(0, Math.min(255, (int) blue))
                );

                image.setRGB(x, y, blurredColor.getRGB());
            }
        }
    }

    private void applyPixelationEffect(BufferedImage image, Rectangle region) {
        int pixelSize = Math.max(PIXELATION_SIZE, Math.min(region.width / 12, region.height / 5)); // Ajustado
        System.out.println("[IMAGE-PROCESSOR] Aplicando pixelização com tamanho: " + pixelSize);

        for (int y = region.y; y < region.y + region.height; y += pixelSize) {
            for (int x = region.x; x < region.x + region.width; x += pixelSize) {

                int endX = Math.min(x + pixelSize, Math.min(region.x + region.width, image.getWidth()));
                int endY = Math.min(y + pixelSize, Math.min(region.y + region.height, image.getHeight()));

                if (x < image.getWidth() && y < image.getHeight()) {
                    Color avgColor = calculateAverageColor(image, x, y, endX, endY);

                    // Preencher o bloco com a cor média
                    Graphics2D g2d = image.createGraphics();
                    g2d.setColor(avgColor);
                    g2d.fillRect(x, y, endX - x, endY - y);
                    g2d.dispose();
                }
            }
        }
    }

    private Color calculateAverageColor(BufferedImage image, int startX, int startY, int endX, int endY) {
        long totalRed = 0, totalGreen = 0, totalBlue = 0;
        int pixelCount = 0;

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                if (x < image.getWidth() && y < image.getHeight()) {
                    Color color = new Color(image.getRGB(x, y));
                    totalRed += color.getRed();
                    totalGreen += color.getGreen();
                    totalBlue += color.getBlue();
                    pixelCount++;
                }
            }
        }

        if (pixelCount == 0) return Color.GRAY;

        return new Color(
                (int) (totalRed / pixelCount),
                (int) (totalGreen / pixelCount),
                (int) (totalBlue / pixelCount)
        );
    }

    private void addPrivacyIndicator(BufferedImage image, Rectangle region) {
        Graphics2D g2d = image.createGraphics();

        // Configurações de renderização
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Borda sutil - mais visível
        g2d.setColor(new Color(255, 255, 255, 220)); // Mais opaca
        g2d.setStroke(new BasicStroke(2.5f)); // Ligeiramente mais espessa
        g2d.drawRect(region.x, region.y, region.width, region.height);

        // Texto indicativo se região for grande o suficiente
        if (region.width > 90 && region.height > 25) { // Threshold menor
            addPrivacyText(g2d, region);
        }

        g2d.dispose();
    }

    private void addPrivacyText(Graphics2D g2d, Rectangle region) {
        String text = "PRIVACIDADE";

        // Calcular fonte baseada no tamanho da região
        int fontSize = Math.max(9, Math.min(region.height / 3, region.width / 8)); // Ajustado
        Font font = new Font("Arial", Font.BOLD, fontSize);
        g2d.setFont(font);

        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getAscent();

        // Centralizar texto
        int textX = region.x + (region.width - textWidth) / 2;
        int textY = region.y + (region.height + textHeight) / 2;

        // Verificar se cabe na região
        if (textX > region.x && textY > region.y &&
                textX + textWidth < region.x + region.width &&
                textY < region.y + region.height) {

            // Sombra do texto - mais escura
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.drawString(text, textX + 1, textY + 1);

            // Texto principal
            g2d.setColor(new Color(255, 255, 255, 240)); // Mais opaco
            g2d.drawString(text, textX, textY);
        }
    }

    private byte[] compressImageOptimized(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Usar compressão JPEG com qualidade controlada
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("Nenhum escritor JPEG disponível");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(COMPRESSION_QUALITY);
        }

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }

        byte[] result = baos.toByteArray();
        System.out.println("[IMAGE-PROCESSOR] Imagem comprimida: " + result.length + " bytes (qualidade: " +
                (COMPRESSION_QUALITY * 100) + "%)");

        return result;
    }

    // Métodos de cache e utilitários
    public ProcessingResult getProcessingStatus(String processId) {
        System.out.println("[IMAGE-PROCESSOR] Consultando status para " + processId);
        ProcessingResult result = processingCache.get(processId);

        if (result != null) {
            System.out.println("[IMAGE-PROCESSOR] Status encontrado: " + result.getStatus());
        } else {
            System.out.println("[IMAGE-PROCESSOR] Status não encontrado para " + processId);
        }

        return result;
    }

    public void clearProcessingResult(String processId) {
        System.out.println("[IMAGE-PROCESSOR] Limpando resultado para " + processId);
        ProcessingResult removed = processingCache.remove(processId);
        if (removed != null) {
            System.out.println("[IMAGE-PROCESSOR] Resultado removido com sucesso");
        } else {
            System.out.println("[IMAGE-PROCESSOR] Nenhum resultado encontrado para remover");
        }
    }

    public int getCacheSize() {
        int size = processingCache.size();
        System.out.println("[IMAGE-PROCESSOR] Tamanho atual do cache: " + size);
        return size;
    }

    // Método para limpar cache antigo (opcional)
    public void cleanupOldResults() {
        System.out.println("[IMAGE-PROCESSOR] Executando limpeza de cache...");
        int initialSize = processingCache.size();

        // Aqui você pode implementar lógica para remover resultados antigos
        // Por exemplo, baseado em timestamp dos ProcessingResult

        int finalSize = processingCache.size();
        System.out.println("[IMAGE-PROCESSOR] Cache limpo: " + initialSize + " -> " + finalSize + " entradas");
    }

    // Método para estatísticas (opcional)
    public void logProcessingStatistics() {
        System.out.println("[IMAGE-PROCESSOR] === Estatísticas de Processamento ===");
        System.out.println("[IMAGE-PROCESSOR] Cache size: " + processingCache.size());
        System.out.println("[IMAGE-PROCESSOR] Qualidade de compressão: " + (COMPRESSION_QUALITY * 100) + "%");
        System.out.println("[IMAGE-PROCESSOR] Timeout configurado: " + PROCESSING_TIMEOUT_MS + "ms");
        System.out.println("[IMAGE-PROCESSOR] ===================================");
    }
}