## 1. Specify safe offload metadata

- [x] 1.1 Characterize the current unlimited llama.cpp GPU-layer mapping and
  device failure evidence.
- [x] 1.2 Define the catalog field, legacy default, validation rules, and
  physical-device boundary.
- [x] 1.3 Add and strictly validate the `local-llm-hub` specification delta.

## 2. Implement bounded offload

- [x] 2.1 Parse and validate optional llama.cpp GPU-layer metadata.
- [x] 2.2 Propagate the configured value through model resolution.
- [x] 2.3 Replace the unlimited engine default with eight layers and include the
  effective count in native load compatibility.
- [x] 2.4 Configure the checked-in Llama 3.2 3B model for a bounded initial
  eight-layer physical test.
- [x] 2.5 Preserve CPU-only and CPU-fallback behavior.
- [x] 2.6 Record the failed eight-layer physical result and move the checked-in
  Llama profile to experimental CPU-only.
- [x] 2.7 Add experimental CPU-only LFM2.5 1.2B and Ministral 3 3B text
  profiles with official artifact integrity metadata.

## 3. Verify and document

- [x] 3.1 Add parser and engine tests for legacy defaults, explicit budgets,
  invalid configurations, CPU-only loading, and reload on budget change.
- [x] 3.2 Update README/project documentation with the bounded-offload policy.
- [x] 3.3 Run targeted tests and the complete quality gate.
- [x] 3.4 Build and copy the debug APK for physical-device validation.
- [x] 3.5 Validate repeated multi-turn Llama 3.2 partial offload on the target
  physical device; reject it after GPU-fence ANR and invalid-logits evidence.
- [ ] 3.6 Validate repeated multi-turn CPU-only Llama 3.2, LFM2.5, and Ministral
  inference, Portuguese quality, memory, responsiveness, and thermal behavior
  on the target physical device.
