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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LicensePlateDetector {

    @Value("${tesseract.data.path:/usr/share/tesseract-ocr/4.00/tessdata}")
    private String tesseractDataPath;

    @Value("${tesseract.language:eng}")
    private String tesseractLanguage;

    private final Tesseract tesseract;

    // Padrões de placa brasileira
    private static final Pattern MERCOSUL_PATTERN = Pattern.compile("^[A-Z]{3}[0-9][A-Z][0-9]{2}$");
    private static final Pattern OLD_PATTERN = Pattern.compile("^[A-Z]{3}[0-9]{4}$");

    public LicensePlateDetector() {
        this.tesseract = new Tesseract();
        configureTesseract();
    }

    private void configureTesseract() {
        try {
            tesseract.setDatapath(tesseractDataPath);
            tesseract.setLanguage(tesseractLanguage);
            tesseract.setPageSegMode(8); // Single word
            tesseract.setOcrEngineMode(1); // Neural nets LSTM engine

            // Configurações específicas para placas
            tesseract.setVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
            tesseract.setVariable("tessedit_pageseg_mode", "8");

            System.out.println("Tesseract configurado com sucesso");
        } catch (Exception e) {
            System.err.println("Erro ao configurar Tesseract: " + e.getMessage());
        }
    }

    public PlateDetectionResult detectPlate(byte[] imageData) {
        try {
            // Converter byte array para BufferedImage
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                return new PlateDetectionResult(false, null, null, null);
            }

            // Detectar região da placa usando análise de componentes conectados
            List<Rectangle> plateRegions = detectPlateRegionsSimple(image);

            for (Rectangle plateRect : plateRegions) {
                // Extrair região da placa
                BufferedImage plateROI = extractROI(image, plateRect);

                if (plateROI != null) {
                    // Pré-processar imagem da placa
                    BufferedImage processedPlate = preprocessPlateImage(plateROI);

                    // Executar OCR
                    String plateText = performOCR(processedPlate);

                    if (plateText != null && !plateText.isEmpty()) {
                        String cleanText = cleanPlateText(plateText);
                        String format = detectPlateFormat(cleanText);

                        if (format != null) {
                            return new PlateDetectionResult(true, cleanText, format, plateRect);
                        }
                    }
                }
            }

            // Se não encontrou placa em regiões específicas, tenta OCR na imagem inteira
            String fullImageText = performOCR(image);
            if (fullImageText != null && !fullImageText.isEmpty()) {
                String cleanText = cleanPlateText(fullImageText);
                String format = detectPlateFormat(cleanText);

                if (format != null) {
                    // Usar dimensões da imagem inteira como região
                    Rectangle fullRect = new Rectangle(0, 0, image.getWidth(), image.getHeight());
                    return new PlateDetectionResult(true, cleanText, format, fullRect);
                }
            }

            return new PlateDetectionResult(false, null, null, null);

        } catch (Exception e) {
            System.err.println("Erro na detecção da placa: " + e.getMessage());
            e.printStackTrace();
            return new PlateDetectionResult(false, null, null, null);
        }
    }

    private List<Rectangle> detectPlateRegionsSimple(BufferedImage image) {
        List<Rectangle> regions = new ArrayList<>();

        // Análise simples baseada em proporções típicas de placas
        int width = image.getWidth();
        int height = image.getHeight();

        // Dividir a imagem em regiões e procurar por áreas com características de placa
        int regionWidth = width / 4;
        int regionHeight = height / 4;

        for (int y = height / 2; y < height - regionHeight; y += regionHeight / 2) {
            for (int x = 0; x < width - regionWidth; x += regionWidth / 2) {
                Rectangle region = new Rectangle(x, y, regionWidth, regionHeight);

                // Verificar se a região pode conter uma placa (análise básica de cor e contraste)
                if (couldContainPlate(image, region)) {
                    regions.add(region);
                }
            }
        }

        // Se não encontrou regiões específicas, usar algumas regiões padrão
        if (regions.isEmpty()) {
            // Região central inferior (onde normalmente ficam as placas)
            regions.add(new Rectangle(width / 4, height * 2 / 3, width / 2, height / 6));
            // Região central
            regions.add(new Rectangle(width / 4, height / 2, width / 2, height / 4));
            // Imagem inteira como última opção
            regions.add(new Rectangle(0, 0, width, height));
        }

        return regions;
    }

    private boolean couldContainPlate(BufferedImage image, Rectangle region) {
        try {
            // Análise básica de contraste e cor na região
            BufferedImage roi = image.getSubimage(region.x, region.y, region.width, region.height);

            // Calcular média de brilho
            long totalBrightness = 0;
            int pixelCount = 0;

            for (int y = 0; y < roi.getHeight(); y++) {
                for (int x = 0; x < roi.getWidth(); x++) {
                    Color color = new Color(roi.getRGB(x, y));
                    int brightness = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                    totalBrightness += brightness;
                    pixelCount++;
                }
            }

            double avgBrightness = (double) totalBrightness / pixelCount;

            // Placas brasileiras tendem a ter brilho moderado a alto (branca/cinza)
            return avgBrightness > 80 && avgBrightness < 220;

        } catch (Exception e) {
            return true; // Em caso de erro, incluir a região
        }
    }

    private BufferedImage extractROI(BufferedImage image, Rectangle rect) {
        try {
            // Garantir que o retângulo está dentro dos limites da imagem
            int x = Math.max(0, rect.x);
            int y = Math.max(0, rect.y);
            int width = Math.min(rect.width, image.getWidth() - x);
            int height = Math.min(rect.height, image.getHeight() - y);

            if (width <= 0 || height <= 0) {
                return null;
            }

            return image.getSubimage(x, y, width, height);
        } catch (Exception e) {
            System.err.println("Erro ao extrair ROI: " + e.getMessage());
            return null;
        }
    }

    private BufferedImage preprocessPlateImage(BufferedImage plateROI) {
        try {
            // Redimensionar para melhor OCR (mantendo proporção)
            int targetWidth = 400;
            int targetHeight = (int) (plateROI.getHeight() * ((double) targetWidth / plateROI.getWidth()));

            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = resized.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(plateROI, 0, 0, targetWidth, targetHeight, null);
            g2d.dispose();

            // Converter para escala de cinza e aplicar filtros simples
            BufferedImage processed = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

            for (int y = 0; y < targetHeight; y++) {
                for (int x = 0; x < targetWidth; x++) {
                    Color color = new Color(resized.getRGB(x, y));
                    int gray = (int) (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());

                    // Aplicar threshold simples
                    gray = gray > 128 ? 255 : 0;

                    Color newColor = new Color(gray, gray, gray);
                    processed.setRGB(x, y, newColor.getRGB());
                }
            }

            return processed;

        } catch (Exception e) {
            System.err.println("Erro no pré-processamento: " + e.getMessage());
            return plateROI; // Retornar imagem original em caso de erro
        }
    }

    private String performOCR(BufferedImage image) {
        try {
            String result = tesseract.doOCR(image);
            return result != null ? result.trim() : null;
        } catch (TesseractException e) {
            System.err.println("Erro no OCR: " + e.getMessage());
            return null;
        }
    }

    private String cleanPlateText(String rawText) {
        if (rawText == null) return null;

        // Remover caracteres especiais e espaços
        String cleaned = rawText.replaceAll("[^A-Z0-9]", "").toUpperCase();

        // Correções comuns de OCR baseadas na posição
        if (cleaned.length() >= 7) {
            StringBuilder sb = new StringBuilder(cleaned);

            // Para placas Mercosul: posição 3 deve ser número, posição 4 deve ser letra
            if (cleaned.length() >= 4) {
                char pos3 = cleaned.charAt(3);
                if (pos3 == 'O') sb.setCharAt(3, '0');
                if (pos3 == 'I') sb.setCharAt(3, '1');
                if (pos3 == 'S') sb.setCharAt(3, '5');
            }

            // Posições 5-6 devem ser números
            for (int i = 5; i < Math.min(cleaned.length(), 7); i++) {
                char c = cleaned.charAt(i);
                if (c == 'O') sb.setCharAt(i, '0');
                if (c == 'I') sb.setCharAt(i, '1');
                if (c == 'S') sb.setCharAt(i, '5');
            }

            cleaned = sb.toString();
        }

        return cleaned;
    }

    private String detectPlateFormat(String plateText) {
        if (plateText == null || plateText.length() < 7) return null;

        String sevenChars = plateText.substring(0, Math.min(7, plateText.length()));

        if (MERCOSUL_PATTERN.matcher(sevenChars).matches()) {
            return "MERCOSUL";
        } else if (OLD_PATTERN.matcher(sevenChars).matches()) {
            return "ANTIGA";
        }

        return null;
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