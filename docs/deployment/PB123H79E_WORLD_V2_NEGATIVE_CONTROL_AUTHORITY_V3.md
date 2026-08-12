# PB1.2.3H7.9E — Negative-control authority V3

## Purpose

Negative controls must demonstrate that a real write/read boundary rejects the
invalid state. A source-only comparator must not be reported as physical Redis
evidence.

## Control rules

The existing negative-control matrix remains one-to-one and records the entry
point, mutation, physical keys, and expected rejection. Controls that exercise
Redis storage use the real reactive Redis integration fixture. Source controls
are explicitly labelled `SOURCE_PROVEN` and are not counted as Redis physical
controls.

The missing-TTL control now constructs `RedisWorldRepository` with an explicit
invalid TTL policy and invokes `saveInitial` against the integration Redis
connection. It verifies the repository rejects before a write; no reflective
field mutation is used.

The durable-reference control is fail-closed at the same authority used by the
production registry: a synthetic unannotated durable writer is supplied beside
a known writer, discovery retains it, and `Authority.requireComplete()` rejects
the set. This is a negative control for default discovery, not a post-migration
semantic comparator.

## Accounting

The focused matrix reports 33 unique invariants, 27 Redis-physical controls,
6 source-proven controls, zero duplicate invariants, zero proxy credit, and
zero false passes. Provider Redis is not modified by this local validation.
