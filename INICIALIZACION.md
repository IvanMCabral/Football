# Football Manager - Guía de Inicialización y Prueba

## ✅ Estado del Proyecto

He creado los archivos faltantes:
- ✅ `pom.xml` - Configuración de Maven con todas las dependencias
- ✅ `application.yaml` - Configuración de Spring Boot
- ✅ `.env` - Variables de entorno

## 📋 Requisitos Previos

Antes de inicializar el proyecto, necesitas tener instalado:

1. **Java 17 o superior**
   ```powershell
   java -version
   ```
   Si no lo tienes: https://adoptium.net/

2. **Maven 3.8+**
   ```powershell
   mvn -version
   ```
   Si no lo tienes: https://maven.apache.org/download.cgi

3. **PostgreSQL 12+** o **Docker** para ejecutar PostgreSQL

## 🚀 Opción 1: Inicialización con Docker (RECOMENDADO)

### Paso 1: Iniciar PostgreSQL en Docker

```powershell
docker run --name football-db `
  -e POSTGRES_PASSWORD=postgres `
  -e POSTGRES_DB=football_manager `
  -p 5432:5432 `
  -d postgres:15
```

### Paso 2: Verificar que PostgreSQL está corriendo

```powershell
docker ps
```

### Paso 3: Compilar el proyecto

```powershell
mvn clean install
```

Este comando:
- Descarga todas las dependencias
- Compila el código
- Ejecuta los tests
- Genera el JAR ejecutable

### Paso 4: Ejecutar la aplicación

```powershell
mvn spring-boot:run
```

La API estará disponible en: **http://localhost:8080/api/v1**

## 🔧 Opción 2: Inicialización con PostgreSQL Local

### Paso 1: Instalar PostgreSQL

Descarga e instala desde: https://www.postgresql.org/download/windows/

### Paso 2: Crear la base de datos

```powershell
# Conectar a PostgreSQL
psql -U postgres

# Crear la base de datos
CREATE DATABASE football_manager;

# Salir
\q
```

### Paso 3: Configurar las credenciales

Edita el archivo `.env` si tus credenciales son diferentes:
```
DB_USER=tu_usuario
DB_PASSWORD=tu_contraseña
```

### Paso 4: Compilar y ejecutar

```powershell
mvn clean install
mvn spring-boot:run
```

## 🧪 Cómo Probar la API

### 1. Verificar que la aplicación está corriendo

```powershell
curl http://localhost:8080/actuator/health
```

Deberías ver: `{"status":"UP"}`

### 2. Registrar un usuario

```powershell
curl -X POST http://localhost:8080/api/v1/auth/register `
  -H "Content-Type: application/json" `
  -d '{
    "email": "test@example.com",
    "username": "testuser",
    "password": "password123"
  }'
```

Respuesta esperada:
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000
}
```

### 3. Iniciar sesión

```powershell
curl -X POST http://localhost:8080/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

### 4. Usar el token para crear un equipo

Guarda el token de la respuesta anterior y úsalo:

```powershell
$token = "tu_token_aqui"

curl -X POST http://localhost:8080/api/v1/teams `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $token" `
  -d '{
    "name": "Mi Equipo",
    "country": "Argentina",
    "budget": 10000000,
    "formation": "FORMATION_4_3_3"
  }'
```

## 📊 Ejecutar Tests

### Todos los tests
```powershell
mvn test
```

### Un test específico
```powershell
mvn test -Dtest=PlayerTest
mvn test -Dtest=MatchEngineImplTest
```

### Con reporte de cobertura
```powershell
mvn clean test jacoco:report
```
El reporte estará en: `target/site/jacoco/index.html`

## 🔍 Verificar la Base de Datos

### Con Docker:
```powershell
docker exec -it football-db psql -U postgres -d football_manager
```

### Consultas útiles:
```sql
-- Ver todas las tablas
\dt

-- Ver usuarios registrados
SELECT * FROM users;

-- Ver equipos creados
SELECT * FROM teams;

-- Ver jugadores
SELECT * FROM players;

-- Salir
\q
```

## 🐛 Solución de Problemas

### Error: "Connection refused" al iniciar la aplicación
- Verifica que PostgreSQL esté corriendo: `docker ps` o `psql -U postgres`
- Verifica las credenciales en `.env`

### Error: "Port 8080 already in use"
- Cambia el puerto en `.env`: `SERVER_PORT=8081`
- O detén la aplicación que usa el puerto 8080

### Error al compilar: "Cannot find symbol"
- Asegúrate de tener Java 17: `java -version`
- Limpia el proyecto: `mvn clean`

### Error de dependencias de Maven
- Actualiza Maven: `mvn --version`
- Borra la caché: `Remove-Item -Recurse -Force ~\.m2\repository`
- Vuelve a compilar: `mvn clean install`

## 📚 Recursos Adicionales

- **Documentación completa de la API**: Ver `API_DOCUMENTATION.md`
- **Guía de arquitectura**: Ver `ARCHITECTURE.md`
- **Guía de desarrollo**: Ver `DEVELOPMENT.md`

## 🎯 Próximos Pasos

Después de inicializar y probar:
1. Explora los endpoints de la API en `API_DOCUMENTATION.md`
2. Prueba crear jugadores y equipos
3. Simula un partido usando el endpoint `/api/v1/matches/{id}/simulate`
4. Revisa los tests existentes para entender la lógica de negocio

## 📞 Comandos Rápidos de Referencia

```powershell
# Iniciar Docker PostgreSQL
docker start football-db

# Detener Docker PostgreSQL
docker stop football-db

# Ver logs de la aplicación
mvn spring-boot:run

# Compilar sin tests
mvn clean install -DskipTests

# Ver logs de Docker
docker logs -f football-db

# Reiniciar la base de datos (¡CUIDADO! Borra datos)
docker rm -f football-db
# Luego vuelve a crear el contenedor
```
