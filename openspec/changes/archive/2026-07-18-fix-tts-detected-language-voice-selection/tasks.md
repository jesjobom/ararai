## 1. Voice Selection

- [x] 1.1 Implement deterministic installed-voice ranking for a detected
  language tag.
- [x] 1.2 Activate a compatible voice explicitly, with verified language and
  default-voice fallback.
- [x] 1.3 Log language detection, selected voice, and fallback decisions without
  logging response content.

## 2. Verification

- [x] 2.1 Add unit tests for matching language, offline preference, deterministic
  regional selection, missing voice data, and no-match fallback.
- [x] 2.2 Extend Android-boundary coverage to verify detected English can resolve
  to an installed English voice when the device provides one.
- [x] 2.3 Run the repository quality gate and strict OpenSpec validation.
