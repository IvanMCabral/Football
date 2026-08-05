# PB1.2.3H7.1 - Shot coordinate RNG compatibility

## Investigation

The execution runtime is Microsoft OpenJDK 21.0.8 (Java specification 21).
The repository does not hardcode `L32X64MixRandom`, does not call
`RandomGeneratorFactory` and does not set `java.util.secureRandomSeed`.
`ShotCoordinateAttachmentTest` only declares the JDK `RandomGenerator` default
and the production detailed engine uses its existing seeded simulation path.

The historical eight-error report is therefore an environment/test-runtime
diagnostic, not a demonstrated gameplay RNG defect. A fresh focal run on the
current JDK completed all eight attachment tests and the 17 coordinate tests
with zero failures and zero errors. No RNG algorithm, seed, probability or
match result was changed.

If another JDK omits the default generator, the supported remediation is to
pin the project Java runtime or inject a documented generator in test scope;
silently substituting a different production algorithm is not allowed.
