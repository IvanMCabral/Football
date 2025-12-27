# Pruebas con PowerShell - Football Manager

# Variables
$baseUrl = "http://localhost:8080/api/v1"
$token = ""

# Función helper para hacer requests
function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Endpoint,
        [string]$Body = "",
        [bool]$UseToken = $false
    )
    
    $headers = @{
        "Content-Type" = "application/json"
    }
    
    if ($UseToken -and $token) {
        $headers["Authorization"] = "Bearer $token"
    }
    
    $params = @{
        Uri = "$baseUrl$Endpoint"
        Method = $Method
        Headers = $headers
    }
    
    if ($Body) {
        $params["Body"] = $Body
    }
    
    try {
        $response = Invoke-RestMethod @params
        Write-Host "✓ Respuesta exitosa:" -ForegroundColor Green
        $response | ConvertTo-Json -Depth 10
        return $response
    } catch {
        Write-Host "✗ Error:" -ForegroundColor Red
        Write-Host $_.Exception.Message
        if ($_.ErrorDetails.Message) {
            Write-Host $_.ErrorDetails.Message
        }
    }
}

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "  Football Manager - Pruebas API     " -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

# 1. Health Check
Write-Host "1. Health Check" -ForegroundColor Yellow
Invoke-ApiRequest -Method "GET" -Endpoint "/actuator/health"
Write-Host ""

# 2. Register User
Write-Host "2. Registrar Usuario" -ForegroundColor Yellow
$registerBody = @{
    email = "test@example.com"
    username = "testuser"
    password = "password123"
} | ConvertTo-Json

$registerResponse = Invoke-ApiRequest -Method "POST" -Endpoint "/auth/register" -Body $registerBody
Write-Host ""

# 3. Login
Write-Host "3. Login" -ForegroundColor Yellow
$loginBody = @{
    email = "test@example.com"
    password = "password123"
} | ConvertTo-Json

$loginResponse = Invoke-ApiRequest -Method "POST" -Endpoint "/auth/login" -Body $loginBody
if ($loginResponse.accessToken) {
    $token = $loginResponse.accessToken
    Write-Host "✓ Token obtenido correctamente" -ForegroundColor Green
}
Write-Host ""

# 4. Create Team
Write-Host "4. Crear Equipo" -ForegroundColor Yellow
$teamBody = @{
    name = "Real Madrid"
    country = "Spain"
    budget = 500000000
    formation = "FORMATION_4_3_3"
} | ConvertTo-Json

$teamResponse = Invoke-ApiRequest -Method "POST" -Endpoint "/teams" -Body $teamBody -UseToken $true
$teamId = $teamResponse.id
Write-Host ""

# 5. Get Team
Write-Host "5. Obtener Equipo" -ForegroundColor Yellow
Invoke-ApiRequest -Method "GET" -Endpoint "/teams/$teamId" -UseToken $true
Write-Host ""

# 6. Create Player
Write-Host "6. Crear Jugador" -ForegroundColor Yellow
$playerBody = @{
    name = "Lionel Messi"
    age = 36
    position = "RW"
    attributes = @{
        attack = 96
        defense = 40
        technique = 99
        speed = 85
        stamina = 80
        mentality = 95
    }
    marketValue = 30000000
} | ConvertTo-Json -Depth 3

$playerResponse = Invoke-ApiRequest -Method "POST" -Endpoint "/players" -Body $playerBody -UseToken $true
$playerId = $playerResponse.id
Write-Host ""

# 7. Add Player to Team
Write-Host "7. Agregar Jugador al Equipo" -ForegroundColor Yellow
Invoke-ApiRequest -Method "POST" -Endpoint "/teams/$teamId/players/$playerId" -UseToken $true
Write-Host ""

# 8. Get Team Squad
Write-Host "8. Obtener Plantilla del Equipo" -ForegroundColor Yellow
Invoke-ApiRequest -Method "GET" -Endpoint "/teams/$teamId/squad" -UseToken $true
Write-Host ""

# 9. Create Match
Write-Host "9. Crear Partido" -ForegroundColor Yellow
$matchBody = @{
    homeTeamId = $teamId
    awayTeamId = $teamId
    scheduledAt = "2025-12-30T20:00:00Z"
} | ConvertTo-Json

$matchResponse = Invoke-ApiRequest -Method "POST" -Endpoint "/matches" -Body $matchBody -UseToken $true
$matchId = $matchResponse.id
Write-Host ""

# 10. Simulate Match
Write-Host "10. Simular Partido" -ForegroundColor Yellow
Invoke-ApiRequest -Method "POST" -Endpoint "/matches/$matchId/simulate" -UseToken $true
Write-Host ""

# 11. Get Match Result
Write-Host "11. Obtener Resultado del Partido" -ForegroundColor Yellow
Invoke-ApiRequest -Method "GET" -Endpoint "/matches/$matchId" -UseToken $true
Write-Host ""

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "  Pruebas completadas!" -ForegroundColor Green
Write-Host "=====================================" -ForegroundColor Cyan
