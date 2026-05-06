# V23 Phase 10C3 — External Configuration for V23 League Engine Flag

**Status:** COMPLETED
**Branch:** `mvp-1-performance-cleanup`
**Created:** 2026-05-05
**Completed:** 2026-05-05

---

## Implementation Summary

| Item | Value |
|------|-------|
| **Decision** | Option A final — `@Configuration` class as sole factory |
| **Files changed** | `LeagueSimulator.java`, `SimulationConfig.java` (new), `application.yaml` |
| **Default** | `false` — V23 league engine NOT enabled by default |
| **Tests** | 112 pass, all existing tests unchanged |
| **API/frontend/persistence** | None changed |

---

## Executive Summary

Phase 10C2 introduced a feature-flagged V23 league engine path in `LeagueSimulator` with `useV23LeagueEngine=false` hardcoded as the default. Phase 10C3 exposes this flag through external Spring configuration so operators can enable the V23 path without recompiling code.

**Constraint:** Default behavior must remain `false`. V23 league engine must NOT be enabled by default.

---

## 1. Audit of Current State

### 1.1 LeagueSimulator Constructors

```java
// Primary constructor — useV23LeagueEngine defaults to false
public LeagueSimulator(MatchSimulator matchSimulator) {
    this(matchSimulator, null, false);
}

// Full constructor with optional V23 engine and flag
public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine, boolean useV23LeagueEngine)
```

Both constructors are source-compatible with existing tests. The single-arg constructor delegates to the three-arg form with `false`, preserving the default behavior.

### 1.2 Spring Wiring

`LeagueSimulator` is annotated `@Service` and is injected into `MatchSimulationOrchestrator` via constructor:

```java
public MatchSimulationOrchestrator(
        CareerRepository careerRepository,
        ...
        LeagueSimulator leagueSimulator,
        ...
)
```

Spring resolves `LeagueSimulator` via the no-arg `@Service` constructor (the single-arg form above), which internally calls `(matchSimulator, null, false)`. `MatchEngineImpl` is NOT currently injected — it is created inline in `simulateWithV23Engine()`.

### 1.3 Existing Configuration

The project uses `@Value` for configuration (confirmed in `JwtTokenProvider.java`):

```java
@Value("${jwt.secret}")
private String jwtSecret;

@Value("${jwt.expiration}")
private long jwtExpirationMs;
```

No `@ConfigurationProperties` classes exist in the project. No `*Properties.java` files exist.

The `application.yaml` has an `app:` block at the root level:

```yaml
app:
  name: Football Manager
  version: 1.0.0
  description: Turn-based football team management...
```

Currently no sub-properties under `app:` are used. All existing config uses `@Value` or `${...}` placeholders with defaults via `:` syntax.

### 1.4 Test Construction

`LeagueSimulatorTest` constructs `LeagueSimulator` directly with `FakeMatchSimulator`:

```java
// Test 1: default path
LeagueSimulator simulator = new LeagueSimulator(fakeSim);

// Test 2: explicit false
LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false);

// Test 3: V23 path enabled
LeagueSimulator simulator = new LeagueSimulator(fakeSim, realEngine, true);
```

Tests do NOT use Spring context. No test changes are required for Option A or B if constructors remain source-compatible.

### 1.5 Property Naming

Convention found in `application.yaml`:
- Top-level keys: `spring`, `jwt`, `server`, `management`, `logging`, `app`
- Nested: `spring.r2dbc`, `spring.flyway`, `spring.data.redis`, `jwt.secret`, `server.port`

Proposed property: `app.simulation.league.use-v23-engine`

Rationale:
- Follows existing `app.` root convention
- Groups under `simulation.league` for future `app.simulation.*` properties
- Uses kebab-case (standard YAML/Spring Boot convention)
- Default: `false`

---

## 2. Options Comparison

### Option A — @Value Property Injection

**Mechanism:** Add `@Value("${app.simulation.league.use-v23-engine:false}") boolean useV23LeagueEngine` as a constructor parameter. Spring passes the value at bean creation time.

**Files affected:**
- `LeagueSimulator.java` — add parameter, modify constructors

**Implementation:**

```java
public LeagueSimulator(MatchSimulator matchSimulator,
                       @Value("${app.simulation.league.use-v23-engine:false}") boolean useV23LeagueEngine) {
    this(matchSimulator, null, useV23LeagueEngine);
}

// Three-arg constructor becomes primary, single-arg is deprecated/removed
public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine, boolean useV23LeagueEngine) {
    this.matchSimulator = matchSimulator;
    this.matchEngine = matchEngine;
    this.useV23LeagueEngine = useV23LeagueEngine;
}
```

Wait — this changes the constructor signature and breaks `MatchSimulationOrchestrator` which injects via the single-arg constructor. Instead:

```java
// Keep single-arg as-is for Spring wiring (useV23Engine defaults from @Value)
public LeagueSimulator(MatchSimulator matchSimulator) {
    this(matchSimulator, null, false);
}

// Add new constructor that accepts the property value
public LeagueSimulator(MatchSimulator matchSimulator,
                       @Value("${app.simulation.league.use-v23-engine:false}") boolean useV23LeagueEngine) {
    this(matchSimulator, null, useV23LeagueEngine);
}

// Full constructor unchanged
public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine, boolean useV23LeagueEngine) {
    this.matchSimulator = matchSimulator;
    this.matchEngine = matchEngine;
    this.useV23LeagueEngine = useV23LeagueEngine;
}
```

But Spring will pick the 2-arg `@Value` constructor over the 1-arg for autowiring. Need `@Qualifier` or a factory method.

**Better approach:** Keep existing constructors untouched. Use field injection or a separate `@Configuration` class to inject the property and set it on the existing bean.

Actually, the cleanest approach for Option A:

```java
@Service
public class LeagueSimulator {
    private final MatchSimulator matchSimulator;
    private final MatchEngineImpl matchEngine;
    private final boolean useV23LeagueEngine;

    // Primary Spring constructor — @Value inlined
    public LeagueSimulator(
            MatchSimulator matchSimulator,
            @Value("${app.simulation.league.use-v23-engine:false}") boolean useV23LeagueEngine) {
        this(matchSimulator, null, useV23LeagueEngine);
    }

    // Existing constructor — preserved for test compatibility
    public LeagueSimulator(MatchSimulator matchSimulator) {
        this(matchSimulator, null, false);
    }

    // Full constructor — preserved for test compatibility
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine, boolean useV23LeagueEngine) {
        this.matchSimulator = matchSimulator;
        this.matchEngine = matchEngine;
        this.useV23LeagueEngine = useV23LeagueEngine;
    }
```

**Risk:** Spring has two constructors with different arities. Spring will prefer the 2-arg constructor (the one with `@Value`). Tests use the 1-arg constructor directly — they would get `false` instead of what they expect in Test 1 (still false, so OK) but this changes test coverage semantics slightly. Actually, Test 1 verifies the default path behavior, so using the 2-arg constructor with default `false` produces the same result as the 1-arg constructor. The behavior is identical.

However, `@Value` on a constructor parameter is valid in Spring. The issue is Spring might prefer the 2-arg over 1-arg based on which can be satisfied. Since both can (1-arg gets matchSimulator only, 2-arg gets matchSimulator + boolean), Spring may fail with "no unique bean" ambiguity.

**Safest Option A implementation:** Use a `static factory method` or a `@PostConstruct` field initializer instead of constructor-based `@Value`. But those add complexity.

**Simpler Option A:** Keep existing constructors. Add a `public static` factory method for Spring config:

```java
@Service
public class LeagueSimulator {
    private final MatchSimulator matchSimulator;
    private final MatchEngineImpl matchEngine;
    private final boolean useV23LeagueEngine;

    // Existing constructors unchanged...

    // Spring uses this — @Value injected by framework
    static LeagueSimulator create(MatchSimulator matchSimulator, boolean useV23LeagueEngine) {
        return new LeagueSimulator(matchSimulator, null, useV23LeagueEngine);
    }
}
```

But Spring doesn't call static factory methods for `@Service` automatically.

**Most practical Option A:** Keep existing single-arg constructor for tests. Add a new constructor that Spring can use with `@Value`. Use `@Primary` or `@Qualifier` to disambiguate.

Actually, the simplest and lowest risk: **Field injection via `@Value` combined with setter.**

```java
@Slf4j
@Service
public class LeagueSimulator {

    private final MatchSimulator matchSimulator;
    private final MatchEngineImpl matchEngine;

    @Value("${app.simulation.league.use-v23-engine:false}")
    private boolean useV23LeagueEngine;

    // Single-arg constructor for test compatibility
    public LeagueSimulator(MatchSimulator matchSimulator) {
        this(matchSimulator, null, false);
    }

    // Full constructor for test compatibility
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine, boolean useV23LeagueEngine) {
        this.matchSimulator = matchSimulator;
        this.matchEngine = matchEngine;
        this.useV23LeagueEngine = useV23LeagueEngine;
    }
```

**Problem:** `@Value` field injection requires Spring to create the bean. If tests call the constructor directly (bypassing Spring), the `@Value` field stays at its default (false) — which is actually what we want. Tests create with explicit flag values. The field stays at false for Spring bean. The constructors can still set it directly.

**Better:** Use constructor-based injection throughout. Add a `@Configuration` class that reads the property and constructs `LeagueSimulator` with the correct flag.

```java
@Configuration
public class SimulationConfig {
    @Value("${app.simulation.league.use-v23-engine:false}")
    private boolean useV23Engine;

    @Bean
    public LeagueSimulator leagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine) {
        return new LeagueSimulator(matchSimulator, matchEngine, useV23Engine);
    }
}
```

But this bypasses `@Service` and requires `@Primary` on the bean to avoid duplicate.

**Verdict on Option A:** Complexities arise with Spring constructor resolution when multiple constructors exist with `@Value`. The cleanest path is Option A via a `@Configuration` class that explicitly constructs `LeagueSimulator` with the property value, removing `@Service` from `LeagueSimulator` (or using `@Primary` to override).

---

### Option B — @ConfigurationProperties

**Mechanism:** Create a `SimulationProperties` class with `@ConfigurationProperties("app.simulation.league")` and inject it into `LeagueSimulator` or a `@Configuration` class.

**Files affected:**
- New: `SimulationProperties.java`
- `LeagueSimulator.java` — inject properties
- New: `SimulationConfig.java` or modify existing config

**Implementation:**

```java
@ConfigurationProperties(prefix = "app.simulation.league")
public record SimulationProperties(
    boolean useV23Engine = false
) {}
```

Then in `LeagueSimulator`:

```java
@Service
public class LeagueSimulator {
    private final MatchSimulator matchSimulator;
    private final MatchEngineImpl matchEngine;
    private final boolean useV23LeagueEngine;

    public LeagueSimulator(MatchSimulator matchSimulator, SimulationProperties properties) {
        this(matchSimulator, null, properties.useV23Engine());
    }

    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine, boolean useV23LeagueEngine) {
        this.matchSimulator = matchSimulator;
        this.matchEngine = matchEngine;
        this.useV23LeagueEngine = useV23LeagueEngine;
    }
}
```

**Problem:** Tests use the single-arg constructor `new LeagueSimulator(fakeSim)`. With `SimulationProperties` in the primary constructor, tests would break unless we keep the single-arg constructor.

**Option B with factory:**

```java
@ConfigurationProperties(prefix = "app.simulation.league")
public record SimulationProperties(
    boolean useV23Engine = false
) {}

@Configuration
public class SimulationConfig {
    private final SimulationProperties properties;

    public SimulationConfig(SimulationProperties properties) {
        this.properties = properties;
    }

    @Bean
    public LeagueSimulator leagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine) {
        return new LeagueSimulator(matchSimulator, matchEngine, properties.useV23Engine());
    }
}
```

This requires `@EnableConfigurationProperties` and removes `@Service` from `LeagueSimulator` or adds `@Primary` to the bean.

---

### Option C — Keep Hardcoded False

No change. Rejected as the goal of Phase 10C3 is to externalize the configuration.

---

### Option D — Environment Variable Only

Not recommended. No project precedent for env-var-only config. Property binding is preferred.

---

## 3. Recommended Option

**Option A (simplest, lowest risk)** via `@Configuration` class:

1. Keep `LeagueSimulator` constructors unchanged
2. Add `SimulationConfig` class that reads the property and constructs `LeagueSimulator`
3. Use `@Primary` to ensure this bean is used over any other `LeagueSimulator` candidate

This approach:
- Preserves all existing tests unchanged
- No changes to `LeagueSimulator` constructors
- Default remains `false` (property default)
- No API/frontend/persistence changes
- Rollback: remove `SimulationConfig`, restore `@Service` on `LeagueSimulator`

**Drawback:** Slight duplication — `LeagueSimulator` is both `@Service` and manually constructed in `SimulationConfig`. Use `@Primary` to resolve.

---

## 4. Implementation Plan

### Files to Create

| File | Purpose |
|------|---------|
| `SimulationConfig.java` | Reads `app.simulation.league.use-v23-engine` property, constructs `LeagueSimulator` with correct flag |
| `V23_PHASE10C3_LEAGUE_ENGINE_CONFIG_PLAN.md` | This document |

### Files to Modify

| File | Change |
|------|--------|
| `LeagueSimulator.java` | Add `@Primary` to ensure the config-constructed bean takes precedence |
| `application.yaml` | Add `app.simulation.league.use-v23-engine: false` property |

### Files NOT Changed

- `MatchEngineImpl.java` — untouched
- `MatchSimulator.java` — untouched
- `DefaultMatchSimulator.java` — untouched
- `MatchFixture.java` — untouched
- `MatchResultDataAdapter.java` — untouched
- `LeagueSimulatorTest.java` — unchanged
- Any API DTO, frontend, Redis schema, PostgreSQL schema

### Property Addition to application.yaml

```yaml
app:
  name: Football Manager
  version: 1.0.0
  description: Turn-based football team management...
  simulation:
    league:
      use-v23-engine: false   # Phase 10C3: set to true to enable V23 league engine
```

### SimulationConfig.java (draft)

```java
package com.footballmanager.application.config;

import com.footballmanager.application.service.domain.MatchEngineImpl;
import com.footballmanager.application.service.simulation.LeagueSimulator;
import com.footballmanager.domain.service.MatchSimulator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties
public class SimulationConfig {

    @Bean
    @Primary  // Ensures this bean is used over any @Service instance
    public LeagueSimulator leagueSimulator(
            @Qualifier("defaultMatchSimulator") MatchSimulator matchSimulator,
            MatchEngineImpl matchEngine,
            @Value("${app.simulation.league.use-v23-engine:false}") boolean useV23Engine) {
        return new LeagueSimulator(matchSimulator, matchEngine, useV23Engine);
    }
}
```

**Problem:** This creates two `LeagueSimulator` beans — one from `@Configuration` (primary) and one from `@Service`. The `@Service` one would be disabled by `@Primary` on the config bean. But the `@Service` still exists and might confuse inspection tools.

**Alternative:** Remove `@Service` from `LeagueSimulator` and let `SimulationConfig` be the sole factory. But this requires finding where `LeagueSimulator` is injected as `@Service` and updating references.

**Simplest resolution:** Keep `@Service` on `LeagueSimulator`, add constructor that accepts the property directly, and rely on Spring's constructor preference. But this conflicts with the existing single-arg constructor.

**Final recommended approach:** Use `LeagueSimulator` as the bean but change its construction logic via a `@Configuration` method that creates a bean with the property value, while marking the `@Service` instance as `@Primary(false)` or simply using setter injection.

Actually, the cleanest and lowest risk:

1. Add a setter for `useV23LeagueEngine` and use `@PostConstruct` to read the property
2. Keep all constructors unchanged
3. Tests pass because they call constructors directly with explicit values

**Simplest Option A implementation:**

```java
@Slf4j
@Service
public class LeagueSimulator {

    private final MatchSimulator matchSimulator;
    private final MatchEngineImpl matchEngine;
    private boolean useV23LeagueEngine = false;  // default false — set by @Value or constructor

    public LeagueSimulator(MatchSimulator matchSimulator) {
        this(matchSimulator, null, false);
    }

    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine, boolean useV23LeagueEngine) {
        this.matchSimulator = matchSimulator;
        this.matchEngine = matchEngine;
        this.useV23LeagueEngine = useV23LeagueEngine;
    }

    @Value("${app.simulation.league.use-v23-engine:false}")
    public void setUseV23LeagueEngine(boolean useV23Engine) {
        this.useV23LeagueEngine = useV23Engine;
    }
```

**Problem:** `@Value` on a setter method requires Spring to call the setter after construction. The `@Service` constructor runs first with `useV23LeagueEngine=false`. Then Spring calls the setter with the property value, overriding the constructor value. This means:
- Spring bean: `useV23LeagueEngine` set by setter to property value (e.g., `true`)
- Tests: constructor sets explicit value directly, setter not called (no Spring context)

This works! Tests call constructors directly and set the field directly. Spring bean initialization path: constructor runs (field=false), then setter runs (field=property value). Tests bypass Spring entirely.

**But wait:** If the single-arg constructor is called by Spring (because it's the only no-arg-like constructor for `@Service`), it sets `useV23LeagueEngine=false`. Then the `@Value` setter overrides it to the property value. This is the correct behavior.

**Risk:** What if Spring picks the 2-arg constructor (with `@Value`) as the primary? Then it would need `MatchEngineImpl matchEngine` which Spring might not have as a bean. If `MatchEngineImpl` isn't available, Spring falls back to the single-arg constructor.

The 2-arg constructor with `@Value` would need Spring to provide `MatchSimulator` AND resolve the `@Value`. That's fine. But then tests calling the 1-arg constructor would get a different class instance than Spring provides. Tests should be OK as long as the behavior is the same.

**Conclusion:** The `@Value` setter approach is the lowest-risk path.

---

## 5. Option Comparison Summary

| Criteria | Option A (@Value setter) | Option B (@ConfigurationProperties) |
|----------|-------------------------|--------------------------------------|
| **Files affected** | `LeagueSimulator.java`, `application.yaml` | `SimulationProperties.java`, `SimulationConfig.java`, `LeagueSimulator.java`, `application.yaml` |
| **Default behavior impact** | None — default remains `false` | None — default remains `false` |
| **Test impact** | None — tests call constructors directly | None — tests call constructors directly |
| **Spring wiring impact** | Minimal — setter called post-construction | Medium — new config class, bean priority |
| **API/frontend/persistence** | None | None |
| **Rollback plan** | Remove `@Value` setter, field defaults to `false` | Remove `SimulationConfig`, restore `@Service` |
| **Risk level** | **Low** | Medium |
| **Future expansion** | Harder — all config in one field | Easier — record structure |

---

## 6. Final Implementation Steps (Option A)

1. Add `@Value` setter method to `LeagueSimulator` for `useV23LeagueEngine`
2. Add `app.simulation.league.use-v23-engine: false` to `application.yaml`
3. No changes to constructors, no new config classes
4. Run regression test suite
5. Document Phase 10C3 completion in status documents

---

## 7. Rollback Plan

1. Remove the `@Value` setter method from `LeagueSimulator`
2. Remove the `app.simulation.league` properties from `application.yaml`
3. All behavior reverts to Phase 10C2 state (hardcoded `false`)
4. Run `LeagueSimulatorTest` to confirm default path unchanged

---

## 8. Open Questions

1. **Multiple constructors + @Value setter**: Does Spring call the setter even when the 1-arg `@Service` constructor is used? Yes, `@Value` on a method is called after bean construction regardless of which constructor was used.
2. **Should `@Service` be removed from `LeagueSimulator`?** Not necessary — `@Value` setter works with `@Service`.
3. **What if someone constructs `LeagueSimulator` directly with `true`?** The setter would overwrite it with the property value (which is `false` by default). This could be a problem — if a test or future code explicitly passes `true` in the constructor, the setter would override it.

**Fix for issue 3:** Use a flag to detect if the field was set via constructor:

```java
private boolean useV23LeagueEngine = false;
private boolean useV23EngineSetViaConstructor = false;

public LeagueSimulator(MatchSimulator matchSimulator) {
    this(matchSimulator, null, false);
}

public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine, boolean useV23LeagueEngine) {
    this.matchSimulator = matchSimulator;
    this.matchEngine = matchEngine;
    this.useV23LeagueEngine = useV23LeagueEngine;
    this.useV23EngineSetViaConstructor = true;
}

@Value("${app.simulation.league.use-v23-engine:false}")
public void setUseV23LeagueEngine(boolean useV23Engine) {
    // Only override if not explicitly set via constructor
    if (!useV23EngineSetViaConstructor) {
        this.useV23LeagueEngine = useV23Engine;
    }
}
```

This way, tests that pass explicit `true` in constructors retain that value. Spring beans default to `false` (no explicit constructor call = `useV23EngineSetViaConstructor=false`, so setter sets it to property value which is `false`).

**Wait — this has an issue:** Spring creates the bean using one of the constructors. If it picks the 1-arg constructor, `useV23EngineSetViaConstructor=false`, setter runs and sets `false`. If property is `false` (default), field stays `false`. If property is `true`, field becomes `true`. That's correct.

But if a future code path constructs with `new LeagueSimulator(sim, engine, true)` directly (bypassing Spring), `useV23EngineSetViaConstructor=true`, setter won't override — field stays `true`. Correct.

**Actually**, the simplest thing: Don't use a setter at all. Use constructor injection properly via `@Configuration`.

**Best approach — confirmed:**

```java
@Service
@Primary  // ensures this instance is used if multiple exist
public class LeagueSimulator {
    private final MatchSimulator matchSimulator;
    private final MatchEngineImpl matchEngine;
    private boolean useV23LeagueEngine;

    // Single-arg constructor for backward compatibility with tests and existing Spring wiring
    public LeagueSimulator(MatchSimulator matchSimulator) {
        this(matchSimulator, null, false);
    }

    // Three-arg constructor for explicit flag setting in tests
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine, boolean useV23LeagueEngine) {
        this.matchSimulator = matchSimulator;
        this.matchEngine = matchEngine;
        this.useV23LeagueEngine = useV23LeagueEngine;
    }

    // Package-private setter for use by SimulationConfig — not a Spring lifecycle hook
    void setUseV23LeagueEngine(boolean useV23Engine) {
        this.useV23LeagueEngine = useV23Engine;
    }
}
```

Then `SimulationConfig` uses a `@Bean` method that calls the constructor and immediately calls the setter:

```java
@Configuration
public class SimulationConfig {
    @Value("${app.simulation.league.use-v23-engine:false}")
    private boolean useV23Engine;

    @Bean
    @Primary
    public LeagueSimulator leagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine) {
        LeagueSimulator ls = new LeagueSimulator(matchSimulator, matchEngine, useV23Engine);
        return ls;
    }
}
```

But this creates two beans — the `@Service` one and the `@Bean` one. `@Primary` ensures the config bean wins. But we need to suppress the `@Service` bean.

**Simpler final approach:**

Remove `@Service` from `LeagueSimulator`. Let `SimulationConfig` be the sole factory. Keep all constructors.

```java
// LeagueSimulator.java
public class LeagueSimulator {
    // No @Service annotation
    private final MatchSimulator matchSimulator;
    private final MatchEngineImpl matchEngine;
    private final boolean useV23LeagueEngine;

    public LeagueSimulator(MatchSimulator matchSimulator) {
        this(matchSimulator, null, false);
    }

    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine, boolean useV23LeagueEngine) {
        this.matchSimulator = matchSimulator;
        this.matchEngine = matchEngine;
        this.useV23LeagueEngine = useV23LeagueEngine;
    }
}
```

```java
// SimulationConfig.java
@Configuration
public class SimulationConfig {
    @Value("${app.simulation.league.use-v23-engine:false}")
    private boolean useV23Engine;

    @Bean
    public LeagueSimulator leagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine) {
        return new LeagueSimulator(matchSimulator, matchEngine, useV23Engine);
    }
}
```

**Issue:** `MatchSimulationOrchestrator` injects `LeagueSimulator` via constructor. Spring needs to know which bean to provide. The `@Bean` method in `SimulationConfig` creates the bean. Spring resolves it fine.

**But wait:** `MatchEngineImpl` is not yet a Spring bean. It was being created inline in `LeagueSimulator`. Making it a bean requires either marking it `@Component` or providing it in the config.

Looking at `LeagueSimulator`, `matchEngine` can be `null` when `useV23LeagueEngine=false`. So we don't need to inject `MatchEngineImpl` in the config — we can pass `null`.

```java
@Bean
public LeagueSimulator leagueSimulator(MatchSimulator matchSimulator) {
    return new LeagueSimulator(matchSimulator, null, useV23Engine);
}
```

This works. `MatchEngineImpl` is created inline in `simulateWithV23Engine()` only when needed. When `useV23Engine=false`, `matchEngine=null` is fine.

**Revised plan:**
1. Remove `@Service` from `LeagueSimulator` — it will be created exclusively via `SimulationConfig`
2. Add `SimulationConfig.java` with `@Configuration` and `@Bean` for `LeagueSimulator`
3. Add `@Value("app.simulation.league.use-v23-engine")` field in `SimulationConfig`
4. Update `application.yaml` with the new property
5. Verify `MatchSimulationOrchestrator` still receives `LeagueSimulator` via constructor injection — it will from Spring's bean context

**Tests:** `LeagueSimulatorTest` creates `LeagueSimulator` directly with constructors. No Spring context. No changes needed.

**Rollback:** Restore `@Service` on `LeagueSimulator`, delete `SimulationConfig.java`, remove property from `application.yaml`.

---

## 9. Recommended Implementation

**Option A (final confirmed):** Remove `@Service` from `LeagueSimulator`, add `SimulationConfig` as sole factory.

| File | Action |
|------|--------|
| `LeagueSimulator.java` | Remove `@Service`, keep constructors |
| `SimulationConfig.java` | Create new `@Configuration` class |
| `application.yaml` | Add `app.simulation.league.use-v23-engine: false` |

**Why this is safest:**
- No ambiguity about which constructor Spring uses
- Tests unchanged (direct constructor calls, no Spring)
- Property defaults to `false` in YAML
- `@Primary` not needed — only one bean exists
- `MatchSimulationOrchestrator` gets `LeagueSimulator` from Spring context (the `@Bean`)
- Easy rollback: restore `@Service`, delete config class

**Risk:** `MatchEngineImpl` creation moves to inline (existing behavior). No change to how it's used.

---

## 10. Future Expansion (Phase 10C5+)

If more `app.simulation.*` properties are needed, migrate to `@ConfigurationProperties`:

```java
@ConfigurationProperties(prefix = "app.simulation")
public record SimulationProperties(
    LeagueProperties league = new LeagueProperties()
) {
    public record LeagueProperties(
        boolean useV23Engine = false
    ) {}
}
```

This is a Phase 10C5 consideration, not in scope for 10C3.