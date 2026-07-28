## 1. Characterize and validate the supported runtime

- [ ] 1.1 Add characterization tests for current normal-Chat and Voice-Chat
  system-instruction selection and retained LiteRT-LM compatibility.
- [x] 1.2 Build a focused LiteRT-LM tool-calling spike for the checked-in Gemma
  4 E2B and E4B bundles using one deterministic in-memory tool.
- [ ] 1.3 Validate structured call selection, arguments, tool response
  continuation, Portuguese/English behavior, cancellation, and absence of
  protocol-token leakage.
- [ ] 1.4 Record physical-device latency and reliability results and enable
  catalog capability only for bundles that meet the accepted threshold.

## 2. Persist mode-specific instructions and tool preferences

- [x] 2.1 Define checked-in normal-Chat and Voice-Chat default instructions plus
  a non-editable app-owned invariant instruction.
- [x] 2.2 Persist bounded editable instructions and Wikipedia enablement locally
  with backward-compatible defaults.
- [x] 2.3 Compose and normalize the effective system instruction for the origin
  of each new turn.
- [ ] 2.4 Include effective instruction and advertised tool set in retained
  native-conversation compatibility and invalidate stale state safely.
- [ ] 2.5 Add unit tests for defaults, empty customization, restoration,
  persistence, mode switching, and compatibility invalidation.

## 3. Add the instructions-and-tools experience

- [x] 3.1 Add the `Instructions and tools` destination and navigation entry.
- [x] 3.2 Add separate bounded editors for normal Chat and Voice Chat with
  restore-default actions and clear effective-behavior guidance.
- [x] 3.3 Add the Wikipedia enablement control with external-network disclosure,
  current-model compatibility, and unsupported-state explanation.
- [x] 3.4 Preserve an enabled preference across model changes while registering
  the tool only for a compatible selected model.
- [ ] 3.5 Add Compose tests for editing, restoring, disclosure, enablement, and
  compatible/unsupported model states.

## 4. Implement bounded Wikipedia knowledge retrieval

- [x] 4.1 Define runtime-neutral `KnowledgeTool`, `ToolRequest`, `ToolResult`,
  `KnowledgeSource`, and controlled error contracts.
- [x] 4.2 Implement official Wikipedia/MediaWiki search and plain-text
  introductory extract retrieval over HTTPS.
- [ ] 4.3 Validate query/language arguments and fixed endpoints; bound redirects,
  item count, decoded bytes, extract length, and total context contribution.
- [ ] 4.4 Add connection/read/total deadlines and cooperative cancellation tied
  to the owning conversation turn.
- [x] 4.5 Frame retrieved text as untrusted external reference material and
  reject unsupported responses without exposing internal diagnostics.
- [ ] 4.6 Add deterministic tests for multilingual success, no results,
  malformed data, redirects, oversized responses, HTTP errors, timeouts, and
  cancellation using a fake transport.

## 5. Integrate Gemma 4 LiteRT-LM automatic tool calling

- [ ] 5.1 Add explicit per-model knowledge-tool capability metadata to the
  catalog parser, validation, and supported Gemma entries.
- [ ] 5.2 Register `wikipedia_search` through the LiteRT-LM tool provider only
  when preference, model capability, and installed model state are eligible.
- [ ] 5.3 Enable structured automatic tool calling and translate tool progress,
  validated results, controlled errors, and cancellation across the engine
  boundary.
- [ ] 5.4 Enforce at most one Wikipedia call and no automatic retry per user
  turn, without rendering or speaking intermediate protocol content.
- [ ] 5.5 Ensure unsupported runtimes continue normal generation without a
  hidden tool prompt or text-command fallback.
- [ ] 5.6 Add engine/coordinator tests for eligible registration, direct answer,
  successful call, invalid arguments, failure, loop prevention, cancellation,
  and retained-session invalidation.

## 6. Present research progress and sources

- [ ] 6.1 Model transient research progress separately from persisted Chat
  messages and recover both Chat modes after success, failure, or cancellation.
- [ ] 6.2 Persist bounded source provider, title, canonical URL, language, and
  retrieval time atomically with a completed assistant answer.
- [ ] 6.3 Render source links with the associated normal-Chat answer while
  keeping raw extracts and tool protocol out of visible history.
- [ ] 6.4 Keep Voice Chat microphone capture inactive during research, preserve
  the uninterrupted loop, speak only the final answer, and leave sources
  visible in the shared conversation.
- [ ] 6.5 Add persistence and UI tests for progress, source rendering, restart,
  failure, cancellation, and Chat ↔ Voice Chat continuity.

## 7. Quality, privacy, and documentation

- [ ] 7.1 Verify that network requests occur only after Wikipedia enablement and
  only for runtime-requested eligible calls.
- [ ] 7.2 Run targeted preference, catalog, provider, engine, coordinator,
  persistence, Chat, Voice Chat, and Compose tests.
- [ ] 7.3 Run Spotless, Detekt, unit tests, Android Lint, debug app build,
  Android-test compilation, strict OpenSpec validation, and `git diff --check`.
- [ ] 7.4 Physically validate multi-turn normal Chat and hands-free Voice Chat
  with every enabled Gemma bundle, including offline, timeout, cancellation,
  model switching, instruction switching, and process recreation.
- [ ] 7.5 Update README, project documentation, privacy claims, and device
  validation guidance with optional Wikipedia networking, supported models,
  source behavior, limits, and known non-real-time semantics.
