# PB1.2.3H7.4 — Contrato de generación de lifecycle

Cada career persistida tiene `career-generation:{careerId}` con un UUID opaco y
TTL de ownership. La generación no se devuelve por HTTP ni se escribe en logs.

Los writers de career validan mapping, tombstone, generación e índice dentro de
`CareerLifecycleCoordinator.serializeCareer`. La variante token-aware permite a
engines y sesiones rechazar una generación retenida antes de un reset.

Un reset crea el tombstone antes de descubrir claves, detiene registries, limpia
familias, elimina generación/mapping/índice y finalmente el root. El tombstone
se conserva durante errores para bloquear callbacks tardíos y permitir retry.
Una nueva carrera genera un token distinto cuando el anterior fue eliminado.
