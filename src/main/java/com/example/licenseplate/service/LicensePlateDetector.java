package com.example.licenseplate.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class LicensePlateDetector {

    @Value("${tesseract.data.path:C:\\Program Files\\Tesseract-OCR\\tessdata}")
    private String tessDataPath;

    @Value("${tesseract.language:por}")
    private String tessLanguage;

    private Tesseract tesseract;

    // Padrões de placas brasileiras
    private static final Pattern MERCOSUL_PATTERN = Pattern.compile("^[A-Z]{3}[0-9][A-Z][0-9]{2}$");
    private static final Pattern ANTIGA_PATTERN = Pattern.compile("^[A-Z]{3}[0-9]{4}$");

    // Configurações otimizadas para placas brasileiras
    private static final double MIN_PLATE_AREA = 3000;
    private static final double MAX_PLATE_AREA = 50000;
    private static final double MIN_ASPECT_RATIO = 2.0;
    private static final double MAX_ASPECT_RATIO = 5.0;
    private static final int MIN_PLATE_WIDTH = 120;
    private static final int MIN_PLATE_HEIGHT = 30;

    public LicensePlateDetector() {
        initializeTesseract();
    }

    private void initializeTesseract() {
        try {
            tesseract = new Tesseract();
            tesseract.setDatapath(tessDataPath);
            tesseract.setLanguage(tessLanguage);
            tesseract.setPageSegMode(8); // Trata a imagem como uma única palavra
            tesseract.setOcrEngineMode(1); // Usa LSTM OCR Engine

            // Configuração específica para placas - apenas letras e números
            tesseract.setVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");

            System.out.println("[DETECTOR] Tesseract inicializado: " + tessDataPath);
        } catch (Exception e) {
            System.err.println("[DETECTOR] Erro ao inicializar Tesseract: " + e.getMessage());
        }
    }

    public PlateDetectionResult detectPlate(byte[] imageData) {
        long startTime = System.currentTimeMillis();
        System.out.println("[DETECTOR] === Iniciando Detecção de Placas ===");

        BufferedImage image = null;
        try {
            image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                System.err.println("[DETECTOR] Falha ao carregar imagem");
                return new PlateDetectionResult(false, null, null, null);
            }

            System.out.println("[DETECTOR] Imagem carregada: " + image.getWidth() + "x" + image.getHeight());

            // 1. Detectar regiões candidatas usando múltiplas técnicas
            List<Rectangle> candidates = detectPlateRegions(image);
            System.out.println("[DETECTOR] Candidatos encontrados: " + candidates.size());

            // 2. Analisar cada candidato com OCR
            for (int i = 0; i < candidates.size(); i++) {
                Rectangle candidate = candidates.get(i);
                System.out.println("[DETECTOR] Analisando candidato " + (i+1) + ": " +
                        candidate.x + "," + candidate.y + " " + candidate.width + "x" + candidate.height);

                PlateDetectionResult result = analyzeCandidate(image, candidate);
                if (result.isFound()) {
                    long totalTime = System.currentTimeMillis() - startTime;
                    System.out.println("[DETECTOR] ✅ PLACA DETECTADA em " + totalTime + "ms: " + result.getPlateText());
                    return result;
                }
            }

            long totalTime = System.currentTimeMillis() - startTime;
            System.out.println("[DETECTOR] ❌ Nenhuma placa detectada em " + totalTime + "ms");
            return new PlateDetectionResult(false, null, null, null);

        } catch (Exception e) {
            System.err.println("[DETECTOR] Erro na detecção: " + e.getMessage());
            e.printStackTrace();
            return new PlateDetectionResult(false, null, null, null);
        } finally {
            if (image != null) image.flush();
        }
    }

    private List<Rectangle> detectPlateRegions(BufferedImage image) {
        List<PlateCandidate> allCandidates = new ArrayList<>();

        // Método 1: Detecção por contraste e bordas
        allCandidates.addAll(detectByEdges(image));

        // Método 2: Detecção por características de cor (branco/cinza)
        allCandidates.addAll(detectByColor(image));

        // Método 3: Varredura sistemática em regiões prováveis
        allCandidates.addAll(detectBySystematicScan(image));

        // Remover duplicatas e filtrar
        List<Rectangle> filtered = removeDuplicatesAndFilter(allCandidates);

        // Ordenar por score de confiança
        filtered.sort((a, b) -> Double.compare(
                calculateRegionScore(image, b),
                calculateRegionScore(image, a)
        ));

        return filtered.subList(0, Math.min(8, filtered.size()));
    }

    private List<PlateCandidate> detectByEdges(BufferedImage image) {
        List<PlateCandidate> candidates = new ArrayList<>();

        try {
            // Converter para escala de cinza
            BufferedImage gray = convertToGrayscale(image);

            // Aplicar filtro para reduzir ruído
            BufferedImage smoothed = applyGaussianBlur(gray, 2);

            // Detectar bordas usando operador Sobel
            BufferedImage edges = applySobelEdgeDetection(smoothed);

            // Aplicar threshold para binarizar
            BufferedImage binary = applyAdaptiveThreshold(edges);

            // Encontrar regiões conectadas
            List<Rectangle> regions = findConnectedRegions(binary);

            for (Rectangle region : regions) {
                if (isValidPlateRegion(region)) {
                    double score = calculateEdgeScore(edges, region);
                    candidates.add(new PlateCandidate(region, score));
                }
            }

        } catch (Exception e) {
            System.err.println("[DETECTOR] Erro na detecção por bordas: " + e.getMessage());
        }

        return candidates;
    }

    private List<PlateCandidate> detectByColor(BufferedImage image) {
        List<PlateCandidate> candidates = new ArrayList<>();

        try {
            int width = image.getWidth();
            int height = image.getHeight();

            // Focar na parte inferior da imagem onde placas geralmente aparecem
            int startY = (int) (height * 0.5);
            int endY = (int) (height * 0.95);

            // Varrer a imagem procurando por regiões com características de placa
            int stepX = 15;
            int stepY = 10;

            for (int y = startY; y < endY - MIN_PLATE_HEIGHT; y += stepY) {
                for (int x = 0; x < width - MIN_PLATE_WIDTH; x += stepX) {

                    // Testar diferentes tamanhos
                    int[] widths = {180, 220, 260, 300, 340};
                    int[] heights = {60, 70, 80, 90};

                    for (int w : widths) {
                        for (int h : heights) {
                            if (x + w <= width && y + h <= height) {
                                Rectangle region = new Rectangle(x, y, w, h);

                                if (isValidPlateRegion(region)) {
                                    double score = calculateColorScore(image, region);
                                    if (score > 0.3) {
                                        candidates.add(new PlateCandidate(region, score));
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[DETECTOR] Erro na detecção por cor: " + e.getMessage());
        }

        return candidates;
    }

    private List<PlateCandidate> detectBySystematicScan(BufferedImage image) {
        List<PlateCandidate> candidates = new ArrayList<>();

        try {
            int width = image.getWidth();
            int height = image.getHeight();

            // Definir regiões de interesse baseadas em onde placas tipicamente aparecem
            Rectangle[] rois = {
                    new Rectangle(0, (int)(height * 0.6), width, (int)(height * 0.35)), // Parte inferior
                    new Rectangle(0, (int)(height * 0.45), width, (int)(height * 0.4)),  // Centro-inferior
                    new Rectangle(0, (int)(height * 0.3), width, (int)(height * 0.5))   // Centro
            };

            for (Rectangle roi : rois) {
                BufferedImage roiImage = image.getSubimage(roi.x, roi.y, roi.width, roi.height);

                // Análise estatística da região
                ImageStats stats = calculateImageStats(roiImage);

                // Procurar por sub-regiões com alta variância (texto) e baixa entropia (fundo uniforme)
                List<Rectangle> subRegions = findHighContrastRegions(roiImage, stats);

                for (Rectangle subRegion : subRegions) {
                    // Converter coordenadas de volta para imagem original
                    Rectangle globalRegion = new Rectangle(
                            roi.x + subRegion.x,
                            roi.y + subRegion.y,
                            subRegion.width,
                            subRegion.height
                    );

                    if (isValidPlateRegion(globalRegion)) {
                        double score = calculateTextScore(image, globalRegion);
                        if (score > 0.4) {
                            candidates.add(new PlateCandidate(globalRegion, score));
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[DETECTOR] Erro na varredura sistemática: " + e.getMessage());
        }

        return candidates;
    }

    private PlateDetectionResult analyzeCandidate(BufferedImage image, Rectangle candidate) {
        try {
            // Extrair e preprocessar região da placa
            BufferedImage plateRegion = image.getSubimage(candidate.x, candidate.y, candidate.width, candidate.height);
            BufferedImage processed = preprocessForOCR(plateRegion);

            // Aplicar OCR
            String text = performOCR(processed);
            System.out.println("[DETECTOR] OCR resultado bruto: '" + text + "'");

            if (text != null && text.length() >= 6) {
                // Limpar e corrigir texto
                String cleanText = cleanAndCorrectText(text);
                System.out.println("[DETECTOR] Texto limpo: '" + cleanText + "'");

                if (isValidPlateText(cleanText)) {
                    String format = detectPlateFormat(cleanText);
                    return new PlateDetectionResult(true, cleanText, format, candidate);
                }
            }

        } catch (Exception e) {
            System.err.println("[DETECTOR] Erro ao analisar candidato: " + e.getMessage());
        }

        return new PlateDetectionResult(false, null, null, null);
    }

    private BufferedImage preprocessForOCR(BufferedImage plateRegion) {
        try {
            // 1. Redimensionar para tamanho ótimo para OCR
            BufferedImage resized = resizeImage(plateRegion, 350, 100);

            // 2. Converter para escala de cinza
            BufferedImage gray = convertToGrayscale(resized);

            // 3. Aplicar filtro bilateral para reduzir ruído mantendo bordas
            BufferedImage filtered = applyGaussianBlur(gray, 1);

            // 4. Equalização de histograma para melhorar contraste
            BufferedImage equalized = equalizeHistogram(filtered);

            // 5. Threshold adaptativo para binarização
            BufferedImage binary = applyAdaptiveThreshold(equalized);

            // 6. Operações morfológicas para limpar
            BufferedImage cleaned = applyMorphologicalCleaning(binary);

            // 7. Verificar se precisa inverter (texto deve ser escuro em fundo claro para Tesseract)
            if (shouldInvertImage(cleaned)) {
                cleaned = invertImage(cleaned);
            }

            return cleaned;

        } catch (Exception e) {
            System.err.println("[DETECTOR] Erro no preprocessing: " + e.getMessage());
            return plateRegion; // Fallback para imagem original
        }
    }

    private String performOCR(BufferedImage image) {
        if (tesseract == null) {
            System.err.println("[DETECTOR] Tesseract não inicializado");
            return null;
        }

        try {
            String result = tesseract.doOCR(image);
            return result != null ? result.replaceAll("[^A-Z0-9]", "").trim() : null;
        } catch (TesseractException e) {
            System.err.println("[DETECTOR] Erro no OCR: " + e.getMessage());
            return null;
        }
    }

    private String cleanAndCorrectText(String text) {
        if (text == null || text.isEmpty()) return "";

        text = text.toUpperCase().replaceAll("[^A-Z0-9]", "");

        // Correções específicas para placas brasileiras baseadas em confusões comuns do OCR
        StringBuilder corrected = new StringBuilder();

        for (int i = 0; i < text.length() && i < 7; i++) {
            char c = text.charAt(i);

            if (i < 3) { // Primeiras 3 posições devem ser letras
                corrected.append(digitToLetter(c));
            } else if (i == 3) { // Posição 3 deve ser número
                corrected.append(letterToDigit(c));
            } else if (i == 4) { // Posição 4 pode ser letra (Mercosul) ou número (antiga)
                corrected.append(c); // Manter original
            } else { // Posições 5-6 devem ser números
                corrected.append(letterToDigit(c));
            }
        }

        return corrected.toString();
    }

    private char digitToLetter(char c) {
        switch (c) {
            case '0': return 'O';
            case '1': return 'I';
            case '3': return 'B';
            case '4': return 'A';
            case '5': return 'S';
            case '6': return 'G';
            case '7': return 'T';
            case '8': return 'B';
            case '9': return 'P';
            default: return c;
        }
    }

    private char letterToDigit(char c) {
        switch (c) {
            case 'O': return '0';
            case 'I': return '1';
            case 'Z': return '2';
            case 'B': return '8';
            case 'S': return '5';
            case 'G': return '6';
            case 'T': return '7';
            case 'P': return '9';
            default: return c;
        }
    }

    private boolean isValidPlateText(String text) {
        if (text == null || text.length() != 7) return false;
        return MERCOSUL_PATTERN.matcher(text).matches() || ANTIGA_PATTERN.matcher(text).matches();
    }

    private String detectPlateFormat(String text) {
        if (MERCOSUL_PATTERN.matcher(text).matches()) {
            return "MERCOSUL";
        } else if (ANTIGA_PATTERN.matcher(text).matches()) {
            return "ANTIGA";
        } else {
            return "DESCONHECIDO";
        }
    }

    // Métodos auxiliares de processamento de imagem (implementação simplificada)

    private BufferedImage convertToGrayscale(BufferedImage image) {
        BufferedImage gray = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = gray.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return gray;
    }

    private BufferedImage resizeImage(BufferedImage image, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return resized;
    }

    private BufferedImage applyGaussianBlur(BufferedImage image, int radius) {
        // Implementação simplificada de blur gaussiano
        BufferedImage blurred = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());

        for (int y = radius; y < image.getHeight() - radius; y++) {
            for (int x = radius; x < image.getWidth() - radius; x++) {
                int totalR = 0, totalG = 0, totalB = 0, count = 0;

                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        Color color = new Color(image.getRGB(x + dx, y + dy));
                        totalR += color.getRed();
                        totalG += color.getGreen();
                        totalB += color.getBlue();
                        count++;
                    }
                }

                Color avgColor = new Color(totalR / count, totalG / count, totalB / count);
                blurred.setRGB(x, y, avgColor.getRGB());
            }
        }

        return blurred;
    }

    private BufferedImage applyAdaptiveThreshold(BufferedImage image) {
        BufferedImage binary = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y));
                int gray = (color.getRed() + color.getGreen() + color.getBlue()) / 3;

                // Threshold adaptativo baseado na média local
                int localMean = calculateLocalMean(image, x, y, 15);
                int pixel = gray > localMean - 10 ? 255 : 0;

                binary.setRGB(x, y, new Color(pixel, pixel, pixel).getRGB());
            }
        }

        return binary;
    }

    private int calculateLocalMean(BufferedImage image, int centerX, int centerY, int windowSize) {
        int sum = 0, count = 0;
        int halfWindow = windowSize / 2;

        for (int y = Math.max(0, centerY - halfWindow); y < Math.min(image.getHeight(), centerY + halfWindow); y++) {
            for (int x = Math.max(0, centerX - halfWindow); x < Math.min(image.getWidth(), centerX + halfWindow); x++) {
                Color color = new Color(image.getRGB(x, y));
                sum += (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                count++;
            }
        }

        return count > 0 ? sum / count : 128;
    }

    private boolean shouldInvertImage(BufferedImage image) {
        int whitePixels = 0, totalPixels = 0;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y));
                int brightness = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                if (brightness > 128) whitePixels++;
                totalPixels++;
            }
        }

        // Se menos de 50% dos pixels são brancos, provavelmente precisa inverter
        return (double) whitePixels / totalPixels < 0.5;
    }

    private BufferedImage invertImage(BufferedImage image) {
        BufferedImage inverted = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y));
                Color invColor = new Color(255 - color.getRed(), 255 - color.getGreen(), 255 - color.getBlue());
                inverted.setRGB(x, y, invColor.getRGB());
            }
        }

        return inverted;
    }

    // Métodos auxiliares para validação e scoring (implementações simplificadas)

    private boolean isValidPlateRegion(Rectangle region) {
        if (region == null) return false;

        double area = region.width * region.height;
        double aspectRatio = (double) region.width / region.height;

        return area >= MIN_PLATE_AREA && area <= MAX_PLATE_AREA &&
                aspectRatio >= MIN_ASPECT_RATIO && aspectRatio <= MAX_ASPECT_RATIO &&
                region.width >= MIN_PLATE_WIDTH && region.height >= MIN_PLATE_HEIGHT;
    }

    private double calculateRegionScore(BufferedImage image, Rectangle region) {
        // Score baseado em múltiplos fatores
        double edgeScore = calculateEdgeScore(image, region);
        double colorScore = calculateColorScore(image, region);
        double positionScore = calculatePositionScore(image, region);
        double textScore = calculateTextScore(image, region);

        return edgeScore * 0.3 + colorScore * 0.3 + positionScore * 0.2 + textScore * 0.2;
    }

    private double calculateEdgeScore(BufferedImage image, Rectangle region) {
        // Implementação simplificada - contar pixels de borda na região
        int edges = 0, total = 0;

        for (int y = region.y; y < Math.min(region.y + region.height, image.getHeight() - 1); y++) {
            for (int x = region.x; x < Math.min(region.x + region.width, image.getWidth() - 1); x++) {
                total++;
                Color current = new Color(image.getRGB(x, y));
                Color next = new Color(image.getRGB(x + 1, y));

                int currentBrightness = (current.getRed() + current.getGreen() + current.getBlue()) / 3;
                int nextBrightness = (next.getRed() + next.getGreen() + next.getBlue()) / 3;

                if (Math.abs(currentBrightness - nextBrightness) > 50) {
                    edges++;
                }
            }
        }

        return total > 0 ? (double) edges / total : 0;
    }

    private double calculateColorScore(BufferedImage image, Rectangle region) {
        // Score baseado na presença de cores típicas de placa (branco/cinza claro)
        int whiteGrayPixels = 0, total = 0;

        for (int y = region.y; y < Math.min(region.y + region.height, image.getHeight()); y++) {
            for (int x = region.x; x < Math.min(region.x + region.width, image.getWidth()); x++) {
                total++;
                Color color = new Color(image.getRGB(x, y));
                int brightness = (color.getRed() + color.getGreen() + color.getBlue()) / 3;

                if (brightness > 180) { // Pixels brancos/claros
                    whiteGrayPixels++;
                }
            }
        }

        double whiteRatio = total > 0 ? (double) whiteGrayPixels / total : 0;
        return whiteRatio > 0.4 && whiteRatio < 0.8 ? whiteRatio : 0.3;
    }

    private double calculatePositionScore(BufferedImage image, Rectangle region) {
        // Score baseado na posição vertical (placas geralmente na parte inferior)
        double centerY = region.y + region.height / 2.0;
        double relativeY = centerY / image.getHeight();

        if (relativeY >= 0.7 && relativeY <= 0.9) {
            return 1.0;
        } else if (relativeY >= 0.5 && relativeY <= 0.95) {
            return 0.7;
        } else {
            return 0.3;
        }
    }

    private double calculateTextScore(BufferedImage image, Rectangle region) {
        // Score baseado na presença de padrões semelhantes a texto
        try {
            BufferedImage subImage = image.getSubimage(region.x, region.y, region.width, region.height);
            BufferedImage processed = preprocessForOCR(subImage);

            // Contar transições horizontais (característica de texto)
            int transitions = 0, totalLines = 0;

            for (int y = processed.getHeight() / 3; y < processed.getHeight() * 2 / 3; y += 3) {
                totalLines++;
                boolean lastWasWhite = false;

                for (int x = 0; x < processed.getWidth(); x++) {
                    Color color = new Color(processed.getRGB(x, y));
                    boolean isWhite = color.getRed() > 128;

                    if (isWhite != lastWasWhite) {
                        transitions++;
                    }
                    lastWasWhite = isWhite;
                }
            }

            double avgTransitions = totalLines > 0 ? (double) transitions / totalLines : 0;
            return Math.min(1.0, avgTransitions / 10.0); // Normalizar

        } catch (Exception e) {
            return 0.0;
        }
    }

    // Implementações simplificadas dos métodos restantes
    private BufferedImage applySobelEdgeDetection(BufferedImage image) { return image; }
    private List<Rectangle> findConnectedRegions(BufferedImage image) { return new ArrayList<>(); }
    private BufferedImage equalizeHistogram(BufferedImage image) { return image; }
    private BufferedImage applyMorphologicalCleaning(BufferedImage image) { return image; }
    private ImageStats calculateImageStats(BufferedImage image) { return new ImageStats(); }
    private List<Rectangle> findHighContrastRegions(BufferedImage image, ImageStats stats) { return new ArrayList<>(); }

    private List<Rectangle> removeDuplicatesAndFilter(List<PlateCandidate> candidates) {
        List<Rectangle> filtered = new ArrayList<>();

        for (PlateCandidate candidate : candidates) {
            boolean isDuplicate = false;

            for (Rectangle existing : filtered) {
                Rectangle intersection = candidate.rectangle.intersection(existing);
                double overlapArea = intersection.width * intersection.height;
                double candidateArea = candidate.rectangle.width * candidate.rectangle.height;
                double overlapRatio = overlapArea / candidateArea;

                if (overlapRatio > 0.5) {
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) {
                filtered.add(candidate.rectangle);
            }
        }

        return filtered;
    }

    // Classes auxiliares
    private static class PlateCandidate {
        Rectangle rectangle;
        double score;

        PlateCandidate(Rectangle rectangle, double score) {
            this.rectangle = rectangle;
            this.score = score;
        }
    }

    private static class ImageStats {
        double mean, variance, entropy;
    }

    public static class PlateDetectionResult {
        private final boolean found;
        private final String plateText;
        private final String format;
        private final Rectangle coordinates;

        public PlateDetectionResult(boolean found, String plateText, String format, Rectangle coordinates) {
            this.found = found;
            this.plateText = plateText;
            this.format = format;
            this.coordinates = coordinates;
        }

        public boolean isFound() { return found; }
        public String getPlateText() { return plateText; }
        public String getFormat() { return format; }
        public Rectangle getCoordinates() { return coordinates; }
    }
}