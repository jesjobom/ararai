## Implementation

- [x] Capture terminal runtime token counts and classify only incomplete Chat turns near the configured context capacity.
- [x] Persist the optional reason and render distinct localized explanations.
- [x] Add focused regression tests, update documentation, and run the quality gate.
- [x] Validate the new APK non-destructively on the retained SM-S901E.
- [x] Keep the latest context-capacity warning visible when Chat auto-scrolls past a long incomplete message, including after reopening.
- [x] Persist explicit cancellation during model loading and generation, and present its notice at the end of the response.
- [x] Verify cancellation and incomplete-response notices on the connected device.
- [x] Classify non-blank output at or above the native context count as possibly incomplete without discarding or suppressing reporting of the text.
- [x] Persist the warning and render a yellow, localized notice after the answer.
