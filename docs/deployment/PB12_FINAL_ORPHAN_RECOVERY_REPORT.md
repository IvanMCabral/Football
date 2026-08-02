# PB1.2.1 Final Orphan Recovery Report

Date: 2026-08-02

Historical source of truth preserved:

- `docs/deployment/PB12_ACTUAL_SMOKE_LIFECYCLE_FINAL_INDEPENDENT_AUDIT.md`
- Historical verdict: `PB1.2.1 PRODUCTION RUNTIME REJECTED`

## Previous defect

The previous `helper-fails-after-java` mode threw inside the runner after Java PID discovery while the original helper was still alive. That did not prove recovery when the helper process itself died after creating Java.

## Current implementation

`tools/GracefulProcessGroupRunner.cs` now writes a flushed state file immediately after `CreateProcess`:

```json
{
  "javaPid": 1234,
  "processGroupId": 1234,
  "created": true,
  "consoleReady": true
}
```

`tools/run-production-jar-smoke.ps1` waits for that state file and records the Java PID, helper PID, and HTTP port before continuing.

## Real helper death mode

Executable mode:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-production-jar-smoke.ps1 -SkipBuild -LifecycleTestMode helper-fails-after-java
```

The mode now:

1. starts Java through the helper;
2. waits for the flushed state file;
3. kills the original helper deliberately;
4. verifies the original helper exited;
5. verifies Java remains alive;
6. attempts independent recovery using a second helper invocation;
7. falls back to force cleanup if Windows does not allow reattaching to the existing Java console;
8. verifies zero Java/helper process residue and zero port residue;
9. exits non-zero because the helper death was intentionally injected.

Observed result:

```json
{
  "status": "FAIL",
  "failureMode": "helper-fails-after-java",
  "originalHelperExited": true,
  "javaWasAliveAfterHelperExit": true,
  "javaPidRecovered": true,
  "recoverySignalAttempted": true,
  "recoverySignalAttemptedMode": "NONE",
  "recoverySignalUsed": "NONE",
  "recoveryError": "AttachConsole to existing Java process failed",
  "javaGracefulRecoverySucceeded": false,
  "javaForceKillUsedRun1": true,
  "forceKillUsed": true,
  "residualJavaProcesses": 0,
  "residualHelperProcesses": 0,
  "residualPorts": 0
}
```

## Failed recovery fallback mode

Executable mode:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-production-jar-smoke.ps1 -SkipBuild -LifecycleTestMode helper-dead-recovery-signal-fails
```

This mode verifies the fallback path explicitly:

- original helper dead;
- Java alive after helper death;
- recovery signal attempted;
- graceful recovery fails;
- force cleanup used and flagged;
- no Java/helper/process/port residue.

## Windows console limitation

The real second-helper attempt cannot reliably reattach to the Java console once the original helper has died. The runner therefore records the failed graceful recovery honestly and uses force cleanup only in the injected FAIL path. PASS paths still refuse any force kill.

## Verdict for this remediation item

P0 closed locally as fail-closed orphan prevention: Java does not remain orphaned, recovery is attempted independently of the original helper, and fallback cleanup is explicit in the evidence.
