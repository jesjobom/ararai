## 1. Characterize the hub

- [x] 1.1 Inventory destinations, state inputs, callbacks, navigation ownership, previews, suppressions, and existing tests in `ArarAiApp.kt`.
- [x] 1.2 Add missing characterization tests for the first extraction slice and record baseline complexity/size.
- [x] 1.3 Select the smallest cohesive destination with low coupling; do not plan a single-file rewrite.

## 2. Extract incrementally

- [x] 2.1 Move the selected destination and its private presentation helpers behind a narrow parameter/state boundary.
- [x] 2.2 Preserve navigation routes, controller lifecycles, state restoration, accessibility semantics, localization, and visual behavior.
- [x] 2.3 Repeat only for independently reviewable destinations whose characterization remains green.
- [x] 2.4 Remove suppressions made unnecessary by extraction and document any remaining application-shell ownership.

## 3. Validate

- [x] 3.1 Run focused Compose, navigation, configuration, and screenshot/manual journey checks for every extracted destination.
- [x] 3.2 Run the complete quality gate and strict OpenSpec validation.
- [x] 3.3 Record before/after file size and complexity as supporting evidence, not as a substitute for behavior tests.
