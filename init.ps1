# Script de Inicializacion - Football Manager
# Para Windows PowerShell

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "  Football Manager - Inicializacion  " -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

# Verificar Java
Write-Host "Verificando Java..." -ForegroundColor Yellow
$javaVersion = java -version 2>&1 | Select-String "version"
if ($javaVersion) {
    Write-Host "Java encontrado: $javaVersion" -ForegroundColor Green
} else {
    Write-Host "Java no encontrado. Por favor instala Java 17+" -ForegroundColor Red
    Write-Host "  Descarga: https://adoptium.net/" -ForegroundColor Yellow
    exit 1
}

# Verificar Maven
Write-Host "Verificando Maven..." -ForegroundColor Yellow
$mavenVersion = mvn -version 2>&1 | Select-String "Apache Maven"
if ($mavenVersion) {
    Write-Host "Maven encontrado: $mavenVersion" -ForegroundColor Green
} else {
    Write-Host "Maven no encontrado. Por favor instala Maven 3.8+" -ForegroundColor Red
    Write-Host "  Descarga: https://maven.apache.org/download.cgi" -ForegroundColor Yellow
    exit 1
}

# Verificar Docker
Write-Host "Verificando Docker..." -ForegroundColor Yellow
$dockerVersion = docker --version 2>&1
if ($dockerVersion) {
    Write-Host "Docker encontrado: $dockerVersion" -ForegroundColor Green
    $useDocker = $true
} else {
    Write-Host "Docker no encontrado. Necesitaras PostgreSQL instalado localmente." -ForegroundColor Yellow
    $useDocker = $false
}

Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan

# Si Docker esta disponible, iniciar PostgreSQL
if ($useDocker) {
    Write-Host "Deseas iniciar PostgreSQL con Docker? (S/N)" -ForegroundColor Yellow
    $response = Read-Host
    
    if ($response -eq "S" -or $response -eq "s") {
        Write-Host "Iniciando PostgreSQL en Docker..." -ForegroundColor Yellow
        
        # Verificar si el contenedor ya existe
        $existing = docker ps -a --filter "name=football-db" --format "{{.Names}}"
        
        if ($existing -eq "football-db") {
            Write-Host "Contenedor 'football-db' ya existe. Iniciandolo..." -ForegroundColor Yellow
            docker start football-db
        } else {
            Write-Host "Creando nuevo contenedor 'football-db'..." -ForegroundColor Yellow
            docker run --name football-db `
                -e POSTGRES_PASSWORD=postgres `
                -e POSTGRES_DB=football_manager `
                -p 5432:5432 `
                -d postgres:15
        }
        
        Write-Host "PostgreSQL iniciado en puerto 5432" -ForegroundColor Green
        Write-Host "  Usuario: postgres" -ForegroundColor Gray
        Write-Host "  Contrasena: postgres" -ForegroundColor Gray
        Write-Host "  Base de datos: football_manager" -ForegroundColor Gray
        
        # Esperar a que PostgreSQL este listo
        Write-Host "Esperando a que PostgreSQL este listo..." -ForegroundColor Yellow
        Start-Sleep -Seconds 5
    }
}

Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Compilando el proyecto..." -ForegroundColor Yellow
Write-Host ""

# Compilar el proyecto
mvn clean install -DskipTests

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Compilacion exitosa!" -ForegroundColor Green
    Write-Host ""
    Write-Host "=====================================" -ForegroundColor Cyan
    Write-Host "Deseas iniciar la aplicacion ahora? (S/N)" -ForegroundColor Yellow
    $startApp = Read-Host
    
    if ($startApp -eq "S" -or $startApp -eq "s") {
        Write-Host ""
        Write-Host "Iniciando Football Manager..." -ForegroundColor Green
        Write-Host "La API estara disponible en: http://localhost:8080/api/v1" -ForegroundColor Cyan
        Write-Host "Presiona Ctrl+C para detener la aplicacion" -ForegroundColor Yellow
        Write-Host ""
        mvn spring-boot:run
    } else {
        Write-Host ""
        Write-Host "Para iniciar la aplicacion manualmente, ejecuta:" -ForegroundColor Yellow
        Write-Host "  mvn spring-boot:run" -ForegroundColor Cyan
    }
} else {
    Write-Host ""
    Write-Host "Error en la compilacion. Revisa los mensajes de error arriba." -ForegroundColor Red
    Write-Host ""
    Write-Host "Soluciones comunes:" -ForegroundColor Yellow
    Write-Host "  1. Verifica que PostgreSQL este corriendo" -ForegroundColor Gray
    Write-Host "  2. Verifica las credenciales en .env" -ForegroundColor Gray
    Write-Host "  3. Ejecuta: mvn clean" -ForegroundColor Gray
    exit 1
}

Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Inicializacion completada!" -ForegroundColor Green
Write-Host "=====================================" -ForegroundColor Cyan
