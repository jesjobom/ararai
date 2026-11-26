## 1. Android sharing

- [x] 1.1 Accept and validate single plain-text and image share intents on cold and warm launches.
- [x] 1.2 Reuse Chat session selection and image import to populate the selected conversation's composer.

## 2. Verification

- [x] 2.1 Cover supported and invalid intent payloads and verify that choosing a session does not send a message.
- [x] 2.2 Run the repository quality gate and record any checks not executed.

The quality gate passed using the CI-pinned OpenSpec 1.6.0. The debug APK
manifest was checked for both MIME types. On November 26, 2026, JJ manually
verified that sharing both text and an image worked with new and existing
conversations on the Galaxy S22 (SM-S901E, debug build 202611252159).
