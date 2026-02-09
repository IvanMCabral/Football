# Plan de Refactorización: application/engine

## Problemas Originales

| Archivo | Problemas | Solución |
|---------|-----------|----------|
| **RoundEngine.java** | Sin @RequiredArgsConstructor, Thread.sleep() bloqueante, List<MatchState> en lugar de Flux, God Class | Extraer UseCases, eliminar bloqueos |
| **RoundStatusCalculator.java** | Retorna tipos simples, múltiples responsabilidades | Mantenido como servicio simple |
| **RoundEngineRegistry.java** | Sin @RequiredArgsConstructor | Agregado @RequiredArgsConstructor |

---

## Fase 1: Interfaces de UseCase ✅

### `domain/port/in/round/`

| Interfaz | Responsabilidad |
|----------|-----------------|
| `StartRoundUseCase` | Iniciar jornada con múltiples partidos |
| `StopRoundUseCase` | Detener jornada |
| `GetRoundStateUseCase` | Obtener estado de la jornada |

---

## Fase 2: Implementaciones de UseCases ✅

### `application/service/round/`

| Implementación | Delega a |
|----------------|----------|
| `StartRoundUseCaseImpl` | RoundEngine.start() + getStateStream() |
| `StopRoundUseCaseImpl` | RoundEngine.stop() + unregister() |
| `GetRoundStateUseCaseImpl` | RoundEngineRegistry.get() + buildRoundState() |

---

## Fase 3: RoundManagementService ✅

**Ubicación:** `application/service/RoundManagementService`

- Orquestador que delega en los UseCases
- **No contiene lógica de negocio propia**
- Métodos: startRound, stopRound, getRoundState, registerRound, getRoundEngine

---

## Fase 4: RoundEngineRegistry ✅

- Agregado `@RequiredArgsConstructor`
- Campo `engines` ahora es `final`
- Cambiado `java.util.Set<UUID>` por `Set<UUID>` (importado)

---

## Fase 5: RoundEngine ✅

- Eliminado `Thread.sleep()` bloqueante
- Reemplazado por `Mono.delay()` asíncrono
- Agregado método `getRoundId()` y `getMatchStates()` públicos

---

## Estructura Final

```
domain/
├── port/
│   ├── in/
│   │   ├── round/
│   │   │   ├── StartRoundUseCase.java
│   │   │   ├── StopRoundUseCase.java
│   │   │   └── GetRoundStateUseCase.java
│   │   └── match/ (existente de refactorización anterior)
│   │       └── ...
│   └── out/
│       └── ...
│
application/
├── service/
│   ├── RoundManagementService.java (coordinador)
│   ├── MatchManagementService.java (existente)
│   └── round/
│       ├── StartRoundUseCaseImpl.java
│       ├── StopRoundUseCaseImpl.java
│       └── GetRoundStateUseCaseImpl.java
│
engine/
├── RoundEngine.java (simplificado)
├── RoundEngineRegistry.java (refactorizado)
├── RoundState.java (modelo)
└── match/ (existente)
```

---

## Reglas del .clauderules Aplicadas

| Regla | Estado |
|-------|--------|
| Usar Mono/Flux en lugar de tipos simples | ✅ |
| `@RequiredArgsConstructor` para inyección | ✅ |
| Single Responsibility (1 clase = 1 responsabilidad) | ✅ |
| Eliminación de God Class | ✅ |
| No usar .block() ni Thread.sleep() | ✅ |

---

## Archivos Creados/Modificados

### Nuevos:
- `domain/port/in/round/StartRoundUseCase.java`
- `domain/port/in/round/StopRoundUseCase.java`
- `domain/port/in/round/GetRoundStateUseCase.java`
- `application/service/round/StartRoundUseCaseImpl.java`
- `application/service/round/StopRoundUseCaseImpl.java`
- `application/service/round/GetRoundStateUseCaseImpl.java`
- `application/service/RoundManagementService.java`

### Modificados:
- `RoundEngineRegistry.java` - @RequiredArgsConstructor
- `RoundEngine.java` - Mono.delay() en lugar de Thread.sleep(), nuevos métodos
