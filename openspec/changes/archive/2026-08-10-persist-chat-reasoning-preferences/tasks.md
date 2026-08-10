## 1. Persistent preferences

- [x] 1.1 Extend in-memory and SharedPreferences Chat preference stores with
  independent reasoning-request and reasoning-visibility booleans defaulting to
  false.
- [x] 1.2 Persist changes from the normal Chat settings controls.

## 2. Capability-aware restoration

- [x] 2.1 Restore stored choices during Chat ViewModel initialization when the
  selected model supports each corresponding capability.
- [x] 2.2 Keep effective settings off for unsupported models without erasing the
  stored choices, and restore them after selecting a supporting model.

## 3. Validation

- [x] 3.1 Add preference recreation and independent-value tests.
- [x] 3.2 Add Chat ViewModel recreation and model-capability transition tests.
- [x] 3.3 Run targeted tests, the complete quality gate, and strict OpenSpec
  validation.
