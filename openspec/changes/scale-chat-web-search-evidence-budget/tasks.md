## Implementation

- [ ] Pass the selected model's effective context-token setting to the model-facing `web_search` adapter and calculate the per-call character budget.
- [ ] Make Chat and widget evidence policies explicit without changing widget tool contracts or limits.
- [ ] Budget the complete serialized Chat tool response, retain ranked source/excerpt alignment, and handle a too-small result with a controlled failure.
- [ ] Add focused tests for context sizes, per-call independence, Unicode/JSON overhead, and unchanged widget behavior.
- [ ] Update relevant documentation and run targeted tests, the project quality gate, strict OpenSpec validation, and non-destructive device checks.
