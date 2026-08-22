## 1. Resolve the baseline

- [x] 1.1 Confirm the supported Gradle/AGP Java range and choose the canonical Android Gradle runtime.
- [x] 1.2 Add a failing repository check for disagreement among workflow configuration, labels, and documented prerequisites.

## 2. Align automation and documentation

- [x] 2.1 Configure the Android quality workflow and local instructions to use the selected runtime consistently.
- [x] 2.2 Isolate Firebase emulator Java requirements when they differ from the Gradle baseline.
- [x] 2.3 Update README, project context, and quality-gate documentation without changing Kotlin/JVM target semantics.

## 3. Validate

- [x] 3.1 Run the canonical quality gate on the selected Java runtime.
- [x] 3.2 Run Firebase rule tests on their documented runtime.
- [x] 3.3 Run strict OpenSpec validation.
