# Plan de Implementación: Refactorización de MatchEngine

## Objetivo
Eliminar la God Class `MatchEngine` extrayendo su lógica a 5 UseCases independientes y un servicio de coordinación.

## Problema Original
- `MatchEngine.java` tenía 10+ métodos de negocio violando SRP
- Inyección manual en lugar de `@RequiredArgsConstructor`
- Lógica de persistencia mezclada con lógica de negocio

---

## Fase 1: Interfaces de UseCase ✅

### Ubicación: `domain/port/in/match/`

| Interfaz | Responsabilidad |
|----------|-----------------|
| `StartMatchUseCase` | Iniciar simulación del partido |
| `PauseMatchUseCase` | Pausar partido en curso |
| `ResumeMatchUseCase` | Reanudar partido pausado |
| `StopMatchUseCase` | Detener/cancelar partido |
| `ExecuteMatchCommandUseCase` | Ejecutar comandos tácticos |

---

## Fase 2: Componentes Internos ✅

### `MatchSession.java` - Sesión interna de partido
- Gestiona el estado, streaming y cola de comandos
- Es un componente interno, no un UseCase

### `MatchSessionRegistry.java` - Registro de sesiones
- Thread-safe usando ConcurrentHashMap
- Crea y gestiona sesiones de partido

### `MatchStatePort.java` - Puerto de salida adicional
- Puerto de salida para acceso al estado en memoria

---

## Fase 3: Implementaciones de UseCases ✅

### Ubicación: `application/service/match/`

| Implementación | Delega a |
|----------------|----------|
| `StartMatchUseCaseImpl` | MatchSession.start() |
| `PauseMatchUseCaseImpl` | MatchSession.pause() |
| `ResumeMatchUseCaseImpl` | MatchSession.resume() |
| `StopMatchUseCaseImpl` | MatchSession.stop() |
| `ExecuteMatchCommandUseCaseImpl` | MatchSession.queueCommand() |

---

## Fase 4: MatchManagementService ✅

**Ubicación:** `application/service/MatchManagementService`

- Orquestador que delega en los UseCases
- **No contiene lógica de negocio propia**
- Retorna Mono/Flux sin romper la cadena reactiva

---

## Fase 5: MatchEngine Registry y Controller ✅

### `MatchEngineRegistry.java`
- Ahora delega en `MatchSessionRegistry`
- Mantiene compatibilidad con API existente

### `MatchEngine.java` (wrapper)
- Transformado en wrapper simple de `MatchSession`
- Algunos métodos lanzan `UnsupportedOperationException`
- Animando a usar los UseCases directamente

### `MatchEngineController.java`
- Actualizado para usar `MatchManagementService`
- Nuevos endpoints: pause, resume, stop

---

## Estructura Final

```
domain/
├── port/
│   ├── in/
│   │   └── match/
│   │       ├── StartMatchUseCase.java
│   │       ├── PauseMatchUseCase.java
│   │       ├── ResumeMatchUseCase.java
│   │       ├── StopMatchUseCase.java
│   │       └── ExecuteMatchCommandUseCase.java
│   └── out/
│       ├── MatchStateRepository.java (existente)
│       └── MatchStatePort.java (nuevo)
│
application/
├── service/
│   ├── MatchManagementService.java (coordinador)
│   └── match/
│       ├── StartMatchUseCaseImpl.java
│       ├── PauseMatchUseCaseImpl.java
│       ├── ResumeMatchUseCaseImpl.java
│       ├── StopMatchUseCaseImpl.java
│       ├── ExecuteMatchCommandUseCaseImpl.java
│       ├── MatchSession.java (sesión interna)
│       └── MatchSessionRegistry.java (registro)
│
engine/
├── MatchEngine.java (wrapper de compatibilidad)
└── MatchEngineRegistry.java (wrapper de compatibilidad)
```

---

## Reglas Aplicadas del .clauderules

| Regla | Estado |
|-------|--------|
| Usar Mono/Flux en lugar de tipos simples | ✅ |
| `@RequiredArgsConstructor` para inyección | ✅ |
| Single Responsibility (1 clase = 1 responsabilidad) | ✅ |
| Puerto de salida para persistencia | ✅ |
| Eliminación de God Class | ✅ |
| MatchManagementService sin lógica de negocio | ✅ |
| No usar .subscribe() ni .block() en orquestador | ✅ |

---

## Archivos Creados

```
domain/port/in/match/
├── StartMatchUseCase.java
├── PauseMatchUseCase.java
├── ResumeMatchUseCase.java
├── StopMatchUseCase.java
└── ExecuteMatchCommandUseCase.java

domain/port/out/
└── MatchStatePort.java

application/service/
├── MatchManagementService.java
└── match/
    ├── StartMatchUseCaseImpl.java
    ├── PauseMatchUseCaseImpl.java
    ├── ResumeMatchUseCaseImpl.java
    ├── StopMatchUseCaseImpl.java
    ├── ExecuteMatchCommandUseCaseImpl.java
    ├── MatchSession.java
    └── MatchSessionRegistry.java
```

---

## Migración Gradual

La arquitectura actual permite una migración gradual:
1. `MatchEngine` existe como wrapper de compatibilidad
2. `MatchEngineController` usa `MatchManagementService`
3. Los UseCases pueden usarse directamente donde sea necesario
4. Eventualmente `MatchEngine` y `MatchEngineRegistry` pueden eliminarse
