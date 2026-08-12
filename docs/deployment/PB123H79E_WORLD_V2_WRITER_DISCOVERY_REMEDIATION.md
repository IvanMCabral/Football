# PB1.2.3H7.9E — Writer discovery remediation

## Finding closed

The previous audit identified a circular rule: only classes already carrying
`@WorldPersistedWriter` were scanned. An unannotated durable adapter therefore
disappeared from the authority and `requireComplete()` could pass.

## Remediation

The discovery path now reads product bytecode independently of writer
annotations. It detects Redis and R2DBC boundaries from class hierarchies and
method/field descriptors, then applies annotations only as classification.
Classpath resources are enumerated through Spring metadata, so interfaces and
compiled adapters are included even when they are not Spring beans. Nested
helpers are not promoted to boundaries unless explicitly inspected.

`WorldMigrationPersistedRootAuthority` retains the compatibility overload used
by focused tests, while the production path is complete-classpath discovery.
Unclassified boundaries are retained in the returned authority and cause the
durable reference registry to fail closed.

## Validation

- test-compile: PASS;
- `WorldMigrationPersistedWriterAuthorityTest`: PASS;
- `WorldMigrationReferenceInventoryTest`: PASS;
- `WorldStorageV2NegativeControlsTest`: PASS;
- no gameplay, fixtures, datasets, frontend, or provider state changed.
