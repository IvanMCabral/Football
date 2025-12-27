# Development Guide

## IDE Setup

### IntelliJ IDEA

1. **Open Project**
   - File → Open → Select project root
   - Let IntelliJ index files

2. **Configure SDK**
   - File → Project Structure → Project
   - Set SDK: Java 17
   - Set Language level: 17

3. **Enable Annotation Processing**
   - File → Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   - Enable annotation processing
   - Enable for project

4. **Install Plugins**
   - Marketplace → Search and install:
     - Spring Boot
     - Lombok
     - Database Tools and SQL
     - RestClient

### VS Code

1. **Install Extensions**
   - Extension Pack for Java
   - Spring Boot Extension Pack
   - REST Client
   - GitLens

2. **Configure Launch**
   - Create `.vscode/launch.json`
   ```json
   {
     "version": "0.2.0",
     "configurations": [
       {
         "type": "java",
         "name": "Football Manager",
         "request": "launch",
         "mainClass": "com.footballmanager.FootballManagerApplication",
         "projectName": "football-manager",
         "cwd": "${workspaceFolder}",
         "console": "integratedTerminal",
         "env": {
           "DB_HOST": "localhost",
           "DB_PORT": "5432",
           "DB_NAME": "football_manager",
           "DB_USER": "postgres",
           "DB_PASSWORD": "postgres"
         }
       }
     ]
   }
   ```

## Local Development Setup

### 1. Database Setup

**Using Docker (Recommended)**
```bash
# Start PostgreSQL
docker run --name football-db \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=football_manager \
  -p 5432:5432 \
  -v football_db_data:/var/lib/postgresql/data \
  -d postgres:15

# Verify connection
docker exec -it football-db psql -U postgres -d football_manager -c "SELECT 1"

# Stop database
docker stop football-db

# Start database again
docker start football-db

# Remove database (careful!)
docker rm -v football-db
```

**Using PostgreSQL Locally**
```bash
# macOS
brew install postgresql@15
brew services start postgresql@15

# Linux (Ubuntu)
sudo apt-get install postgresql postgresql-contrib
sudo systemctl start postgresql

# Windows
# Download installer from https://www.postgresql.org/download/windows/
# Run installer, note password

# Create database
createdb -U postgres football_manager

# Test connection
psql -U postgres -d football_manager
```

### 2. Environment Configuration

**Create `.env` file**
```bash
cat > .env << 'EOF'
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=football_manager
DB_USER=postgres
DB_PASSWORD=postgres

# JWT
JWT_SECRET=dev-secret-key-change-in-production

# Application
APP_PORT=8080
EOF
```

**Load environment variables (optional)**
```bash
# Add to .bashrc or .zshrc
export $(cat .env | grep -v '^#' | xargs)
```

### 3. Build and Run

**Build Project**
```bash
mvn clean install

# Skip tests (faster)
mvn clean install -DskipTests

# Build specific module
mvn clean install -pl :football-manager
```

**Run Application**
```bash
# Using Maven
mvn spring-boot:run

# Using IDE
Right-click FootballManagerApplication.java → Run

# Using Docker
docker build -t football-manager .
docker run -p 8080:8080 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=football_manager \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  football-manager

# Using Java directly
java -jar target/football-manager.jar
```

**Verify Application Started**
```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# Expected response
{
  "status": "UP"
}
```

## Running Tests

### Unit Tests
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=PlayerTest

# Run specific test method
mvn test -Dtest=PlayerTest#shouldCreatePlayer

# Run with verbose output
mvn test -X
```

### Integration Tests
```bash
# Run with active profile
mvn test -Dspring.profiles.active=test

# Run integration tests only
mvn verify -DskipUnitTests
```

### Test Coverage
```bash
# Generate coverage report
mvn clean test jacoco:report

# View report
open target/site/jacoco/index.html  # macOS
start target\site\jacoco\index.html # Windows
xdg-open target/site/jacoco/index.html # Linux
```

## API Testing

### Using cURL

**Register User**
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email":"user@example.com",
    "username":"testuser",
    "password":"password123"
  }' | jq .
```

**Login**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email":"user@example.com",
    "password":"password123"
  }' | jq .
```

**Create Team (requires token)**
```bash
TOKEN="eyJhbGciOiJIUzUxMiJ9..."

curl -X POST http://localhost:8080/api/v1/teams \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name":"Manchester United",
    "country":"England",
    "initialBudget":50000000
  }' | jq .
```

### Using REST Client (VS Code)

**File: `requests.http`**
```http
### Variables
@baseUrl = http://localhost:8080/api/v1
@token = eyJhbGciOiJIUzUxMiJ9...

### Register
POST {{baseUrl}}/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "username": "testuser",
  "password": "password123"
}

### Login
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}

### Get Teams
GET {{baseUrl}}/teams
Authorization: Bearer {{token}}
```

### Using Postman

1. **Import Collection**
   - Create new collection "Football Manager"

2. **Add Requests**
   - POST /auth/register
   - POST /auth/login
   - GET /teams
   - POST /teams
   - etc.

3. **Set Variables**
   - Collection Variables:
     - `baseUrl` = http://localhost:8080/api/v1
     - `token` = (set after login)

4. **Create Tests**
   - Add test scripts for each endpoint
   - Automate token extraction from login response

## Debugging

### Enable Debug Logging

**In `application.yaml`**
```yaml
logging:
  level:
    com.footballmanager: DEBUG
    org.springframework.security: DEBUG
    org.springframework.r2dbc: DEBUG
```

**Or via environment variable**
```bash
export LOGGING_LEVEL_COM_FOOTBALLMANAGER=DEBUG
mvn spring-boot:run
```

### Remote Debugging

**Start with Debug Port**
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

**Connect in IDE**
- IntelliJ: Run → Edit Configurations → Add Remote → localhost:5005
- VS Code: Debug → Add Configuration → Java Attach

### Common Issues

**Port Already in Use**
```bash
# Find process using port 8080
lsof -i :8080  # macOS/Linux
netstat -ano | findstr :8080  # Windows

# Kill process
kill -9 <PID>  # macOS/Linux
taskkill /PID <PID> /F  # Windows
```

**Database Connection Failed**
```bash
# Check database is running
docker ps | grep football-db

# Verify credentials
psql -h localhost -U postgres -d football_manager -c "SELECT 1"

# Check logs
docker logs football-db
```

**JWT Token Invalid**
- Ensure `JWT_SECRET` matches in .env
- Check token expiration (24 hours)
- Verify Authorization header format: `Bearer {token}`

## Database Management

### Connect to Database

```bash
# Using psql
psql -h localhost -U postgres -d football_manager

# Using Docker
docker exec -it football-db psql -U postgres -d football_manager
```

### Useful Queries

**Check Tables**
```sql
\dt  -- List all tables

SELECT * FROM information_schema.tables
WHERE table_schema = 'public';
```

**View Migrations Applied**
```sql
SELECT * FROM flyway_schema_history;
```

**Reset Database**
```sql
-- Dangerous! Only in development
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

### Backup and Restore

```bash
# Backup
pg_dump -h localhost -U postgres football_manager > backup.sql

# Restore
psql -h localhost -U postgres football_manager < backup.sql

# Using Docker
docker exec football-db pg_dump -U postgres football_manager > backup.sql
```

## Code Style

### Formatting

**IntelliJ IDEA**
- Code → Reformat Code (Ctrl+Alt+L)

**VS Code**
- Format Document (Shift+Alt+F)

### Linting

**Enable Checkstyle**
```xml
<!-- In pom.xml -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.3.1</version>
</plugin>
```

**Run checks**
```bash
mvn checkstyle:check
```

## Git Workflow

### Branching Strategy (Git Flow)

```bash
# Feature branch
git checkout -b feature/training-system

# Make changes, commit
git add .
git commit -m "feat: add training system"

# Push and create pull request
git push origin feature/training-system

# Merge to main (via PR)
```

### Commit Messages

Follow Conventional Commits:
```
feat: add training system
fix: correct team budget calculation
refactor: simplify match simulation
test: add player attribute tests
docs: update API documentation
chore: update dependencies
```

## Performance Profiling

### JVM Profiling

```bash
# Run with JFR (Java Flight Recorder)
java -XX:+FlightRecorder -XX:FlightRecorderOptions=defaultrecording=true,dumponexit=true \
  -jar target/football-manager.jar

# Analyze with JMC (Java Mission Control)
```

### Database Query Analysis

**Enable Query Logging**
```yaml
logging:
  level:
    org.springframework.r2dbc: TRACE
```

**Check Slow Queries**
```bash
# Enable PostgreSQL slow query log
ALTER SYSTEM SET log_min_duration_statement = 1000;
SELECT pg_reload_conf();
```

## Docker Development

### Compose File

**File: `docker-compose.yml`**
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: football_manager
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  app:
    build: .
    depends_on:
      - postgres
    ports:
      - "8080:8080"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_USER: postgres
      DB_PASSWORD: postgres

volumes:
  postgres_data:
```

**Run**
```bash
docker-compose up -d      # Start services
docker-compose logs -f    # View logs
docker-compose down       # Stop services
```

## Useful Commands

```bash
# Clean build
mvn clean

# Compile only
mvn compile

# Run tests
mvn test

# Create executable JAR
mvn package

# Install to local Maven repository
mvn install

# Run specific main class
mvn exec:java -Dexec.mainClass="com.footballmanager.FootballManagerApplication"

# Dependency tree
mvn dependency:tree

# Check for outdated dependencies
mvn versions:display-dependency-updates
```
