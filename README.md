# 🚗 API de Detecção de Placas Veiculares

[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green.svg)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Uma API REST robusta desenvolvida em Java Spring Boot que detecta placas de veículos brasileiros em imagens, extrai o texto via OCR, aplica blur na região da placa e comprime a imagem. Suporta placas Mercosul (ABC1D23) e formato antigo (ABC1234).

## 🎯 Funcionalidades

- ✅ **Detecção Automática**: Identifica placas brasileiras em imagens
- ✅ **OCR Inteligente**: Extrai texto usando Tesseract com correções específicas
- ✅ **Suporte Dual**: Placas Mercosul e formato antigo
- ✅ **Processamento de Imagem**: Aplica blur gaussiano na placa detectada
- ✅ **Compressão Inteligente**: Otimiza tamanho da imagem mantendo qualidade
- ✅ **API Assíncrona**: Processamento não-bloqueante com sistema de polling
- ✅ **Pronto para Produção**: Docker, Nginx, SSL, monitoramento
- ✅ **Cliente Node.js**: Biblioteca pronta para integração

## 📋 Índice

- [Instalação Local (Windows)](#-instalação-local-windows)
- [Instalação em VPS Ubuntu](#-instalação-em-vps-ubuntu)
- [Configuração para GitHub](#-configuração-para-github)
- [Docker & Produção](#-docker--produção)
- [API Documentation](#-api-documentation)
- [Cliente Node.js](#-cliente-nodejs)
- [Monitoramento](#-monitoramento)
- [Troubleshooting](#-troubleshooting)

---

## 🖥️ Instalação Local (Windows)

### Pré-requisitos
- **Java 17+** - [Download OpenJDK](https://openjdk.java.net/)
- **Maven 3.6+** - [Download Maven](https://maven.apache.org/download.cgi) ou usar IntelliJ
- **IntelliJ IDEA** - [Download](https://www.jetbrains.com/idea/)
- **Tesseract OCR** - [Download Windows](https://github.com/UB-Mannheim/tesseract/wiki)

### 1. Instalar Tesseract OCR

1. **Baixar e instalar:**
   ```
   https://github.com/UB-Mannheim/tesseract/wiki
   ```
   - Execute como administrador
   - Instale em: `C:\Program Files\Tesseract-OCR\`

2. **Baixar dados em português (opcional):**
   ```
   https://github.com/tesseract-ocr/tessdata/raw/main/por.traineddata
   ```
   - Salve em: `C:\Program Files\Tesseract-OCR\tessdata\por.traineddata`

3. **Verificar instalação:**
   ```cmd
   tesseract --version
   tesseract --list-langs
   ```

### 2. Configurar Projeto

1. **Clone/baixe o projeto:**
   ```bash
   git clone https://github.com/your-username/license-plate-api.git
   cd license-plate-api
   ```

2. **Configurar application.properties:**
   ```properties
   # Windows paths
   tesseract.data.path=C:\\Program Files\\Tesseract-OCR\\tessdata
   tesseract.language=eng  # ou por se instalou português
   
   server.port=8080
   server.servlet.context-path=/api
   
   spring.servlet.multipart.max-file-size=10MB
   spring.servlet.multipart.max-request-size=10MB
   
   image.compression.quality=0.8
   image.max.width=1920
   image.max.height=1080
   
   logging.level.com.example.licenseplate=DEBUG
   ```

3. **Abrir no IntelliJ:**
   - File → Open → Selecione a pasta do projeto
   - Aguarde download das dependências Maven
   - Se aparecer popup "Maven project needs to be imported", clique em Import

### 3. Executar

1. **Via IntelliJ:**
   - Navegue: `src/main/java/.../LicensePlateApiApplication.java`
   - Clique no ▶️ verde ou `Shift + F10`

2. **Via linha de comando:**
   ```bash
   mvn spring-boot:run
   ```

### 4. Testar

```bash
# Health check
curl http://localhost:8080/api/license-plate/health

# Upload imagem
curl -X POST -F "image=@caminho/para/imagem.jpg" http://localhost:8080/api/license-plate/process
```

---

## 🐧 Instalação em VPS Ubuntu

### Método Rápido (Recomendado)

```bash
# 1. Fazer upload do script de deploy
wget https://raw.githubusercontent.com/your-username/license-plate-api/main/deploy.sh
chmod +x deploy.sh

# 2. Instalar dependências
./deploy.sh prod --install

# 3. Reiniciar sistema
sudo reboot

# 4. Após reiniciar, fazer deploy
./deploy.sh prod --build

# 5. (Opcional) Configurar SSL
./deploy.sh prod --ssl seu-dominio.com
```

### Método Manual Detalhado

#### 1. Preparar Servidor
```bash
# Atualizar sistema
sudo apt update && sudo apt upgrade -y

# Instalar dependências básicas
sudo apt install -y \
    openjdk-17-jdk \
    maven \
    git \
    curl \
    wget \
    unzip \
    nginx \
    certbot \
    python3-certbot-nginx

# Instalar Tesseract OCR
sudo apt install -y tesseract-ocr tesseract-ocr-por

# Verificar instalação
java -version
mvn -version
tesseract --version
```

#### 2. Instalar Docker
```bash
# Remover versões antigas
sudo apt-get remove docker docker-engine docker.io containerd runc

# Adicionar repositório oficial
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Instalar Docker
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Instalar Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/download/v2.20.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Adicionar usuário ao grupo docker
sudo usermod -aG docker $USER
```

#### 3. Configurar Aplicação
```bash
# Clone repositório
git clone https://github.com/your-username/license-plate-api.git
cd license-plate-api

# Configurar para produção
cp src/main/resources/application.properties src/main/resources/application-production.properties

# Editar configurações de produção
nano src/main/resources/application-production.properties
```

Configuração de produção:
```properties
# Configuração para Ubuntu
tesseract.data.path=/usr/share/tesseract-ocr/4.00/tessdata
tesseract.language=por

server.port=8080
server.servlet.context-path=/api

# Logs
logging.level.root=INFO
logging.file.name=/app/logs/license-plate-api.log

# Performance
server.tomcat.max-threads=20
server.tomcat.connection-timeout=20000
```

#### 4. Build e Deploy com Docker
```bash
# Build da aplicação
docker build -t license-plate-api:latest .

# Executar com Docker Compose
docker-compose up -d

# Verificar status
docker-compose ps
docker-compose logs -f
```

#### 5. Configurar Nginx (Proxy Reverso)
```bash
# Criar configuração do site
sudo nano /etc/nginx/sites-available/license-plate-api

# Conteúdo do arquivo:
server {
    listen 80;
    server_name seu-dominio.com www.seu-dominio.com;
    
    client_max_body_size 10M;
    
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
    
    location / {
        return 200 '{"message": "License Plate API", "health": "/api/license-plate/health"}';
        add_header Content-Type application/json;
    }
}

# Ativar site
sudo ln -s /etc/nginx/sites-available/license-plate-api /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

#### 6. Configurar SSL (HTTPS)
```bash
# Gerar certificado SSL
sudo certbot --nginx -d seu-dominio.com -d www.seu-dominio.com

# Configurar renovação automática
sudo crontab -e
# Adicionar linha:
0 12 * * * /usr/bin/certbot renew --quiet
```

#### 7. Configurar Firewall
```bash
# Configurar UFW
sudo ufw allow ssh
sudo ufw allow 'Nginx Full'
sudo ufw --force enable
sudo ufw status
```

---

## 📂 Configuração para GitHub

### Estrutura do Repositório
```
license-plate-api/
├── .github/
│   └── workflows/
│       ├── ci.yml                     # CI/CD Pipeline
│       └── deploy.yml                 # Deploy automático
├── src/
│   └── main/
│       ├── java/
│       └── resources/
│           ├── application.properties
│           └── application-production.properties
├── nodejs-client/
│   ├── nodejs-client.js
│   ├── package.json
│   └── README.md
├── docker/
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── docker-compose.prod.yml
│   └── nginx.conf
├── scripts/
│   ├── deploy.sh
│   ├── backup.sh
│   └── monitor.sh
├── docs/
│   ├── api-documentation.md
│   ├── deployment-guide.md
│   └── examples/
├── .gitignore
├── .dockerignore
├── README.md
├── LICENSE
└── pom.xml
```

### 1. Criar .gitignore
```gitignore
# Compiled class file
*.class

# Log file
*.log
logs/

# Package Files
*.jar
*.war
*.nar
*.ear
*.zip
*.tar.gz
*.rar

# Maven
target/
pom.xml.tag
pom.xml.releaseBackup
pom.xml.versionsBackup
pom.xml.next
release.properties
dependency-reduced-pom.xml
buildNumber.properties
.mvn/timing.properties
.mvn/wrapper/maven-wrapper.jar

# IDE
.idea/
*.iws
*.iml
*.ipr
.vscode/
*.swp
*.swo

# OS
.DS_Store
Thumbs.db

# Application specific
/uploads/
/temp/
application-local.properties

# Docker
.docker/

# Node.js
node_modules/
npm-debug.log*
yarn-debug.log*
yarn-error.log*

# Environment
.env
.env.local
.env.development.local
.env.test.local
.env.production.local

# SSL certificates
ssl/
*.pem
*.key
*.crt

# Backup files
backup/
*.sql
*.dump
```

### 2. Configurar GitHub Actions

Criar `.github/workflows/ci.yml`:
```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Install Tesseract
      run: |
        sudo apt-get update
        sudo apt-get install -y tesseract-ocr tesseract-ocr-por
    
    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
        restore-keys: ${{ runner.os }}-m2
    
    - name: Run tests
      run: mvn clean test
    
    - name: Build application
      run: mvn clean package -DskipTests
    
    - name: Build Docker image
      run: docker build -t license-plate-api:${{ github.sha }} .

  deploy:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Deploy to VPS
      uses: appleboy/ssh-action@v0.1.5
      with:
        host: ${{ secrets.VPS_HOST }}
        username: ${{ secrets.VPS_USERNAME }}
        key: ${{ secrets.VPS_SSH_KEY }}
        script: |
          cd /home/ubuntu/license-plate-api
          git pull origin main
          ./deploy.sh prod --update
```

### 3. Configurar Secrets do GitHub

No GitHub Repository → Settings → Secrets:
```
VPS_HOST: seu-servidor.com
VPS_USERNAME: ubuntu
VPS_SSH_KEY: (sua chave SSH privada)
DOCKER_REGISTRY_TOKEN: (se usar registry privado)
```

---

## 🐳 Docker & Produção

### Docker Compose para Produção
```yaml
version: '3.8'

services:
  app:
    image: license-plate-api:latest
    container_name: license-plate-api
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - JAVA_OPTS=-Xmx1g -Xms512m -XX:+UseG1GC
    volumes:
      - ./logs:/app/logs
      - /etc/timezone:/etc/timezone:ro
      - /etc/localtime:/etc/localtime:ro
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/license-plate/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    networks:
      - app-network

  nginx:
    image: nginx:alpine
    container_name: license-plate-nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./ssl:/etc/nginx/ssl:ro
      - /var/log/nginx:/var/log/nginx
    depends_on:
      - app
    networks:
      - app-network

  watchtower:
    image: containrrr/watchtower
    container_name: watchtower
    restart: unless-stopped
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    environment:
      - WATCHTOWER_CLEANUP=true
      - WATCHTOWER_POLL_INTERVAL=3600

networks:
  app-network:
    driver: bridge
```

### Comandos Úteis de Produção
```bash
# Status dos containers
docker-compose ps

# Logs em tempo real
docker-compose logs -f

# Atualizar aplicação
git pull
docker-compose build
docker-compose up -d

# Backup dos logs
tar -czf backup-$(date +%Y%m%d).tar.gz logs/

# Monitorar recursos
docker stats

# Limpar containers antigos
docker system prune -f

# Reiniciar apenas a aplicação
docker-compose restart app

# Ver logs específicos
docker-compose logs app
docker-compose logs nginx
```

---

## 📚 API Documentation

### Endpoints

#### 1. Health Check
```http
GET /api/license-plate/health
```

**Resposta:**
```json
{
  "status": "UP",
  "timestamp": 1726689600000,
  "service": "License Plate Detection API",
  "version": "1.0.0"
}
```

#### 2. Processar Imagem
```http
POST /api/license-plate/process
Content-Type: multipart/form-data

image: [arquivo da imagem]
```

**Resposta (Imediata):**
```json
{
  "processId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "message": "Processamento iniciado. Use o processId para verificar o status."
}
```

#### 3. Consultar Status
```http
GET /api/license-plate/status/{processId}
```

**Respostas Possíveis:**

**Processando:**
```json
{
  "status": "PROCESSING",
  "processId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Concluído com Placa:**
```json
{
  "status": "COMPLETED",
  "processId": "550e8400-e29b-41d4-a716-446655440000",
  "licensePlate": "BRA2E19",
  "plateFormat": "MERCOSUL",
  "coordinates": {
    "x": 150,
    "y": 200,
    "width": 300,
    "height": 100
  },
  "processedImageBase64": "/9j/4AAQSkZJRgABAQAAAQ...",
  "processingTimeMs": 1500
}
```

**Concluído sem Placa:**
```json
{
  "status": "COMPLETED",
  "processId": "550e8400-e29b-41d4-a716-446655440000",
  "processedImageBase64": "/9j/4AAQSkZJRgABAQAAAQ...",
  "processingTimeMs": 800
}
```

**Erro:**
```json
{
  "status": "ERROR",
  "processId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Erro ao processar imagem: formato não suportado"
}
```

#### 4. Limpar Resultado
```http
DELETE /api/license-plate/clear/{processId}
```

### Códigos de Status HTTP
- `200 OK` - Sucesso
- `202 Accepted` - Processamento iniciado
- `400 Bad Request` - Dados inválidos
- `404 Not Found` - Process ID não encontrado
- `413 Payload Too Large` - Arquivo muito grande
- `500 Internal Server Error` - Erro interno

### Limites e Restrições
- **Tamanho máximo do arquivo:** 10MB
- **Formatos suportados:** JPEG, PNG, BMP
- **Rate limiting:** 10 requests/minuto por IP (em produção)
- **Timeout:** 60 segundos para processamento

---

## 💻 Cliente Node.js

### Instalação
```bash
npm install axios form-data
```

### Uso Básico
```javascript
const LicensePlateApiClient = require('./nodejs-client');

async function exemplo() {
    const client = new LicensePlateApiClient('http://localhost:8080/api');
    
    try {
        // Processar imagem
        const result = await client.processImage('./foto-carro.jpg');
        
        console.log('Status:', result.status);
        
        if (result.licensePlate) {
            console.log('Placa detectada:', result.licensePlate);
            console.log('Formato:', result.plateFormat);
            console.log('Coordenadas:', result.coordinates);
        }
        
        // Salvar imagem processada
        if (result.processedImageBase64) {
            await client.saveProcessedImage(
                result.processedImageBase64,
                './imagem-processada.jpg'
            );
            console.log('Imagem processada salva!');
        }
        
    } catch (error) {
        console.error('Erro:', error.message);
    }
}

exemplo();
```

### Métodos Disponíveis
```javascript
// Processar imagem (aguarda conclusão automaticamente)
const result = await client.processImage(imagePath);

// Consultar status específico
const status = await client.getStatus(processId);

// Aguardar conclusão manual
const result = await client.waitForCompletion(processId, maxWaitTime);

// Limpar resultado do cache
await client.clearResult(processId);

// Health check
const health = await client.healthCheck();

// Salvar imagem processada
await client.saveProcessedImage(base64Image, outputPath);
```

---

## 📊 Monitoramento

### Logs da Aplicação
```bash
# Ver logs em tempo real
tail -f logs/license-plate-api.log

# Buscar erros
grep ERROR logs/license-plate-api.log

# Estatísticas de processamento
grep "COMPLETED" logs/license-plate-api.log | wc -l
```

### Métricas do Sistema
```bash
# Uso de CPU e memória
htop

# Espaço em disco
df -h

# Status do Docker
docker stats

# Status do serviço
systemctl status license-plate-api
```

### Script de Monitoramento
```bash
#!/bin/bash
# monitor.sh - Script de monitoramento

# Verificar se a API está respondendo
if ! curl -f http://localhost:8080/api/license-plate/health > /dev/null 2>&1; then
    echo "$(date): API não está respondendo - reiniciando..."
    cd /path/to/license-plate-api
    docker-compose restart
    
    # Enviar notificação (opcional)
    # curl -X POST -H 'Content-type: application/json' \
    #   --data '{"text":"License Plate API foi reiniciada"}' \
    #   YOUR_WEBHOOK_URL
fi

# Limpeza de logs antigos
find logs/ -name "*.log" -mtime +30 -delete

# Verificar espaço em disco
DISK_USAGE=$(df / | tail -1 | awk '{print $5}' | sed 's/%//')
if [ "$DISK_USAGE" -gt 85 ]; then
    echo "$(date): Espaço em disco baixo: ${DISK_USAGE}%"
fi
```

### Configurar Crontab
```bash
# Executar monitoramento a cada 5 minutos
crontab -e

# Adicionar linha:
*/5 * * * * /path/to/monitor.sh >> /var/log/license-plate-monitor.log 2>&1
```

---

## 🔧 Troubleshooting

### Problemas Comuns

#### 1. "Tesseract not found"
```bash
# Verificar instalação
tesseract --version
which tesseract

# Ubuntu/Debian
sudo apt install tesseract-ocr tesseract-ocr-por

# Verificar configuração
grep tesseract src/main/resources/application*.properties
```

#### 2. "OpenCV loading failed" (se usar versão OpenCV)
```bash
# Verificar dependências
ldd /path/to/opencv/libraries

# Instalar dependências
sudo apt install libopencv-dev python3-opencv
```

#### 3. "Port 8080 already in use"
```bash
# Verificar processo usando porta
sudo netstat -tulpn | grep 8080
sudo lsof -i :8080

# Matar processo
sudo kill -9 PID

# Ou mudar porta no application.properties
server.port=8081
```

#### 4. "Permission denied" para Docker
```bash
# Adicionar usuário ao grupo docker
sudo usermod -aG docker $USER

# Reiniciar sessão ou usar
newgrp docker

# Verificar permissões
docker ps
```

#### 5. "SSL Certificate expired"
```bash
# Verificar certificado
sudo certbot certificates

# Renovar certificado
sudo certbot renew

# Configurar renovação automática
sudo crontab -e
# Adicionar: 0 12 * * * /usr/bin/certbot renew --quiet
```

#### 6. Performance Issues
```bash
# Verificar recursos
htop
docker stats

# Ajustar configurações JVM
# No docker-compose.yml:
environment:
  - JAVA_OPTS=-Xmx2g -Xms1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200

# Monitorar logs de GC
-XX:+PrintGC -XX:+PrintGCDetails -XX:+PrintGCTimeStamps
```

#### 7. Erro de OCR em placas específicas
```bash
# Verificar qualidade da imagem
# Imagem deve ter pelo menos 200px de largura na região da placa

# Testar OCR manualmente
tesseract imagem.jpg output -l por --psm 8

# Ajustar configurações de pré-processamento
# No LicensePlateDetector.java, ajustar threshold e filtros
```

### Logs de Debug
```properties
# application.properties para debug
logging.level.com.example.licenseplate=DEBUG
logging.level.org.springframework.web=DEBUG

# Ver requisições HTTP
logging.level.org.springframework.web.servlet.DispatcherServlet=DEBUG
```

### Backup e Recuperação
```bash
# Backup completo
./scripts/backup.sh

# Backup manual
tar -czf backup-$(date +%Y%m%d).tar.gz \
  logs/ \
  src/ \
  docker-compose.yml \
  nginx.conf

# Restaurar backup
tar -xzf backup-YYYYMMDD.tar.gz
docker-compose up -d
```

---

## 🤝 Contribuição

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/AmazingFeature`)
3. Commit suas mudanças (`git commit -m 'Add some AmazingFeature'`)
4. Push para a branch (`git push origin feature/AmazingFeature`)
5. Abra um Pull Request

### Guidelines para Contribuição
- Siga os padrões de código Java
- Adicione testes para novas funcionalidades
- Atualize a documentação
- Use commits semânticos
- Teste em ambiente local antes do PR

---

## 📄 Licença

Este projeto está sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para detalhes.

---

## 🆘 Suporte

- **Issues:** [GitHub Issues](https://github.com/your-username/license-plate-api/issues)
- **Discussões:** [GitHub Discussions](https://github.com/your-username/license-plate-api/discussions)
- **Email:** seu-email@exemplo.com

---

## 🙏 Agradecimentos

- [Tesseract OCR](https://github.com/tesseract-ocr/tesseract) - Engine de OCR
- [Spring Boot](https://spring.io/projects/spring-boot) - Framework Java
- [Docker](https://www.docker.com/) - Containerização
- Comunidade Java e contribuidores

---

**Desenvolvido com ❤️ para detectar placas brasileiras**# API de Detecção de Placas Veiculares

Esta é uma API REST desenvolvida em Java Spring Boot que detecta placas de veículos em imagens, aplica blur na região da placa e comprime a imagem.

## Funcionalidades

- ✅ Detecção automática de placas em imagens
- ✅ Suporte aos formatos de placa brasileira (Mercosul e antiga)
- ✅ OCR para extração do texto da placa
- ✅ Aplicação de blur gaussiano na região da placa
- ✅ Compressão inteligente de imagens
- ✅ Processamento assíncrono
- ✅ API REST com endpoints para POST e GET
- ✅ Cliente Node.js incluído

## Pré-requisitos

### Para a API Java:
- Java 17 ou superior
- Maven 3.6+
- OpenCV (incluído via dependência Maven)
- Tesseract OCR

### Para o cliente Node.js:
- Node.js 14+ 
- npm ou yarn

## Instalação

### 1. Instalar Tesseract OCR

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install tesseract-ocr tesseract-ocr-por
```

**Windows:**
- Baixe o instalador do [GitHub do Tesseract](https://github.com/UB-Mannheim/tesseract/wiki)
- Instale e adicione ao PATH
- Baixe os dados de idioma português

**macOS:**
```bash
brew install tesseract tesseract-lang
```

### 2. Configurar a API Java

1. **Clone/crie o projeto:**
```bash
mkdir license-plate-api
cd license-plate-api
```

2. **Copie os arquivos fornecidos para as respectivas pastas:**
```
src/
├── main/
│   ├── java/
│   │   └── com/example/licenseplate/
│   │       ├── LicensePlateApiApplication.java
│   │       ├── controller/
│   │       │   └── LicensePlateController.java
│   │       ├── service/
│   │       │   ├── LicensePlateDetector.java
│   │       │   └── ImageProcessorService.java
│   │       ├── dto/
│   │       │   └── ProcessingResult.java
│   │       ├── config/
│   │       │   └── AsyncConfig.java
│   │       └── exception/
│   │           └── GlobalExceptionHandler.java
│   └── resources/
│       └── application.properties
└── pom.xml
```

3. **Ajustar configurações no `application.properties`:**
```properties
# Ajuste o caminho do Tesseract para seu sistema
tesseract.data.path=/usr/share/tesseract-ocr/4.00/tessdata  # Linux
# tesseract.data.path=C:\\Program Files\\Tesseract-OCR\\tessdata  # Windows
```

4. **Compilar e executar:**
```bash
mvn clean install
mvn spring-boot:run
```

A API estará disponível em: `http://localhost:8080/api`

### 3. Configurar o Cliente Node.js

1. **Criar diretório do cliente:**
```bash
mkdir license-plate-client
cd license-plate-client
```

2. **Instalar dependências:**
```bash
npm init -y
npm install axios form-data
```

3. **Copiar o arquivo `nodejs-client.js` para o diretório**

## Uso da API

### Endpoints Disponíveis

#### 1. POST `/api/license-plate/process`
Envia uma imagem para processamento.

**Parâmetros:**
- `image`: Arquivo de imagem (multipart/form-data)

**Resposta:**
```json
{
  "processId": "uuid-do-processo",
  "status": "PROCESSING",
  "message": "Processamento iniciado"
}
```

#### 2. GET `/api/license-plate/status/{processId}`
Consulta o status do processamento.

**Respostas possíveis:**

**Processando:**
```json
{
  "status": "PROCESSING",
  "processId": "uuid-do-processo"
}
```

**Concluído com placa detectada:**
```json
{
  "status": "COMPLETED",
  "processId": "uuid-do-processo",
  "licensePlate": "ABC1D23",
  "plateFormat": "MERCOSUL",
  "coordinates": {
    "x": 150,
    "y": 200,
    "width": 300,
    "height": 100
  },
  "processedImageBase64": "base64-encoded-image",
  "processingTimeMs": 1500
}
```

**Concluído sem placa detectada:**
```json
{
  "status": "COMPLETED",
  "processId": "uuid-do-processo",
  "processedImageBase64": "base64-encoded-image",
  "processingTimeMs": 800
}
```

#### 3. DELETE `/api/license-plate/clear/{processId}`
Remove o resultado do cache.

#### 4. GET `/api/license-plate/health`
Verifica a saúde da API.

## Exemplo de Uso com Node.js

```javascript
const LicensePlateApiClient = require('./nodejs-client');

async function exemplo() {
    const client = new LicensePlateApiClient('http://localhost:8080/api');
    
    try {
        // Processar imagem
        const result = await client.processImage('./minha-imagem.jpg');
        
        if (result.licensePlate) {
            console.log('Placa encontrada:', result.licensePlate);
            console.log('Formato:', result.plateFormat);
        }
        
        // Salvar imagem processada
        if (result.processedImageBase64) {
            await client.saveProcessedImage(
                result.processedImageBase64, 
                './imagem-processada.jpg'
            );
        }
        
    } catch (error) {
        console.error('Erro:', error.message);
    }
}

exemplo();
```

## Exemplo com cURL

```bash
# Enviar imagem para processamento
curl -X POST \
  http://localhost:8080/api/license-plate/process \
  -H 'Content-Type: multipart/form-data' \
  -F 'image=@/path/to/image.jpg'

# Consultar status (substitua PROCESS_ID pelo ID retornado)
curl -X GET \
  http://localhost:8080/api/license-plate/status/PROCESS_ID
```

## Formatos Suportados

### Imagens:
- JPEG/JPG
- PNG
- BMP

### Placas:
- **Mercosul**: ABC1D23 (3 letras + 1 número + 1 letra + 2 números)
- **Antiga**: ABC1234 (3 letras + 4 números)

## Configurações Avançadas

### Qualidade de Compressão
No `application.properties`:
```properties
image.compression.quality=0.8  # 0.0 a 1.0
image.max.width=1920
image.max.height=1080
```

### Pool de Threads
No `AsyncConfig.java`:
```java
executor.setCorePoolSize(2);      // Threads mínimas
executor.setMaxPoolSize(5);       // Threads máximas
executor.setQueueCapacity(100);   // Fila de processos
```

## Troubleshooting

### Erro: "Failed to load OpenCV"
- Verifique se todas as dependências Maven foram baixadas
- Em alguns sistemas, pode ser necessário instalar OpenCV nativamente

### Erro: "Tesseract not found"
- Verifique se o Tesseract está instalado e no PATH
- Ajuste o caminho no `application.properties`
- Instale os dados de idioma português

### Erro: "Max upload size exceeded"
- Ajuste `spring.servlet.multipart.max-file-size` no properties
- Padrão é 10MB

### Performance baixa na detecção
- Ajuste os parâmetros de detecção no `LicensePlateDetector.java`
- Considere usar imagens com resolução adequada (não muito baixa nem muito alta)

## Estrutura do Projeto

```
license-plate-api/
├── src/main/java/com/example/licenseplate/
│   ├── LicensePlateApiApplication.java     # Classe principal
│   ├── controller/
│   │   └── LicensePlateController.java     # Endpoints REST
│   ├── service/
│   │   ├── LicensePlateDetector.java       # Detecção e OCR
│   │   └── ImageProcessorService.java      # Processamento de imagem
│   ├── dto/
│   │   └── ProcessingResult.java           # Estrutura de resposta
│   ├── config/
│   │   └── AsyncConfig.java                # Configuração async
│   └── exception/
│       └── GlobalExceptionHandler.java     # Tratamento de erros
├── src/main/resources/
│   └── application.properties              # Configurações
├── pom.xml                                 # Dependências Maven
└── nodejs-client/
    ├── nodejs-client.js                    # Cliente Node.js
    ├── package.json                        # Dependências npm
    └── README.md                           # Este arquivo
```

## Contribuição

Sinta-se à vontade para contribuir com melhorias:

1. Fork o projeto
2. Crie uma branch para sua feature
3. Commit suas mudanças
4. Push para a branch
5. Abra um Pull Request

## Licença

Este projeto está sob a licença MIT. Veja o arquivo LICENSE para mais detalhes.
