## 1. Characterize and validate the supported runtime

- [x] 1.1 Add characterization tests for current normal-Chat and Voice-Chat
  system-instruction selection and retained LiteRT-LM compatibility.
- [x] 1.2 Build a focused LiteRT-LM tool-calling spike for the checked-in Gemma
  4 E2B and E4B bundles using one deterministic in-memory tool.
- [x] 1.3 Validate structured call selection, arguments, tool response
  continuation, Portuguese/English behavior, cancellation, and absence of
  protocol-token leakage.
- [x] 1.4 Record physical-device latency and reliability results and enable
  catalog capability only for bundles that meet the accepted threshold.

## 2. Persist mode-specific instructions and tool preferences

- [x] 2.1 Define checked-in normal-Chat and Voice-Chat default instructions plus
  a non-editable app-owned invariant instruction.
- [x] 2.2 Persist bounded editable instructions and Wikipedia enablement locally
  with backward-compatible defaults.
- [x] 2.3 Compose and normalize the effective system instruction for the origin
  of each new turn.
- [x] 2.4 Include effective instruction and advertised tool set in retained
  native-conversation compatibility and invalidate stale state safely.
- [x] 2.5 Add unit tests for defaults, empty customization, restoration,
  persistence, mode switching, and compatibility invalidation.

## 3. Add the instructions-and-tools experience

- [x] 3.1 Add the `Instructions and tools` destination as a Home action above
  Settings, with separate Instructions and Tools tabs.
- [x] 3.2 Add separate bounded editors for normal Chat and Voice Chat with
  restore-default actions and clear effective-behavior guidance.
- [x] 3.3 Add the Wikipedia enablement control with external-network disclosure,
  current-model compatibility, and unsupported-state explanation.
- [x] 3.4 Preserve an enabled preference across model changes while registering
  the tool only for a compatible selected model.
- [x] 3.5 Add Compose tests for editing, restoring, disclosure, enablement, and
  compatible/unsupported model states.

## 4. Implement bounded Wikipedia knowledge retrieval

- [x] 4.1 Define runtime-neutral `KnowledgeTool`, `ToolRequest`, `ToolResult`,
  `KnowledgeSource`, and controlled error contracts.
- [x] 4.2 Implement official Wikipedia/MediaWiki search and plain-text
  introductory extract retrieval over HTTPS.
- [x] 4.3 Validate query/language arguments, response media type, JSON shape, and
  fixed official HTTPS endpoints; reject redirects and non-Wikipedia canonical
  URLs.
- [x] 4.4 Bound result count, wire bytes, decoded characters, title/extract
  length, and total context contribution before data crosses the engine
  boundary.
- [x] 4.5 Add connection/read/total deadlines and cooperative cancellation tied
  to the owning conversation turn, preserving distinct timeout, cancellation,
  unavailable, malformed, and no-result outcomes.
- [x] 4.6 Frame retrieved text as untrusted external reference material and
  reject unsupported responses without exposing internal diagnostics.
- [x] 4.7 Add deterministic tests for multilingual success, no results,
  malformed data, redirects, oversized responses, HTTP errors, timeouts, and
  cancellation using a fake transport.

## 5. Adapt and characterize Wikipedia tool calling

- [x] 5.1 Add a production `OpenApiTool` adapter for `wikipedia_search` with
  strict argument parsing, controlled `ToolResult` serialization, transient
  source capture, and a one-call/no-retry guard per user turn.
- [x] 5.2 Replace the characterization's hard-coded tool executor with the
  production adapter backed by a deterministic fake `KnowledgeTool`.
- [x] 5.3 Expand offline characterization for English/Portuguese success,
  direct answers, follow-up reuse, invalid arguments, no results, controlled
  failure, timeout, cancellation, one-call enforcement, cleanup, and absence of
  visible protocol leakage.
- [x] 5.4 Run the deterministic characterization on physical E4B and E2B
  bundles and record latency/reliability evidence separately for each model.
- [x] 5.5 Add a direct opt-in Wikipedia smoke test to the Tools tab; invoke the
  provider without loading or prompting the model.

## 6. Integrate Gemma 4 LiteRT-LM automatic tool calling

- [x] 6.1 Add explicit per-model knowledge-tool capability metadata to the
  catalog parser, validation, and supported Gemma entries.
- [x] 6.2 Register `wikipedia_search` through the LiteRT-LM tool provider only
  when preference, model capability, and installed model state are eligible.
- [x] 6.3 Enable structured automatic tool calling and translate tool progress,
  validated results, controlled errors, and cancellation across the engine
  boundary.
- [x] 6.4 Enforce at most three Wikipedia calls per user turn, searching English first and
      retrying in the detected question language when supported and needed
  turn, without rendering or speaking intermediate protocol content.
- [x] 6.5 Ensure unsupported runtimes continue normal generation without a
  hidden tool prompt or text-command fallback.
- [x] 6.6 Add engine/coordinator tests for eligible registration, direct answer,
  successful call, invalid arguments, failure, loop prevention, cancellation,
  and retained-session invalidation.

## 7. Present research progress and sources

- [x] 7.1 Model transient research progress separately from persisted Chat
  messages and recover both Chat modes after success, failure, or cancellation.
- [x] 7.2 Persist bounded source provider, title, canonical URL, language, and
  retrieval time atomically with a completed assistant answer.
- [x] 7.3 Render source links with the associated normal-Chat answer while
  keeping raw extracts and tool protocol out of visible history.
- [x] 7.4 Keep Voice Chat microphone capture inactive during research, preserve
  the uninterrupted loop, speak only the final answer, and leave sources
  visible in the shared conversation.
- [x] 7.5 Add persistence and UI tests for progress, source rendering, restart,
  failure, cancellation, and Chat ↔ Voice Chat continuity.

## 8. Quality, privacy, and documentation

- [x] 8.1 Verify that network requests occur only after Wikipedia enablement and
  only for runtime-requested eligible calls.
- [x] 8.2 Run targeted preference, catalog, provider, engine, coordinator,
  persistence, Chat, Voice Chat, and Compose tests.
- [x] 8.3 Run Spotless, Detekt, unit tests, Android Lint, debug app build,
  Android-test compilation, strict OpenSpec validation, and `git diff --check`.
- [ ] 8.4 Physically validate multi-turn normal Chat and hands-free Voice Chat
  with every enabled Gemma bundle, including offline, timeout, cancellation,
  model switching, instruction switching, and process recreation.
  Deferred when this change was archived: deterministic physical tool
  characterization passed for E2B and E4B, and the available integrated manual
  checks passed, but the complete cross-product matrix above was not executed.
  Continue this validation from `docs/device-validation.md` during subsequent
  development.
- [x] 8.5 Update README, project documentation, privacy claims, and device
  validation guidance with optional Wikipedia networking, supported models,
  source behavior, limits, and known non-real-time semantics.

## 9. Refine shared history and diagnostics

- [x] 9.1 Persist reasoning generated by Voice Chat with the completed
  assistant message while keeping it hidden on the Voice Chat screen.
- [x] 9.2 Show Voice Chat reasoning in normal Chat when `Show reasoning` is
  enabled.
- [x] 9.3 Position normal Chat at the newest message when opening Chat or
  switching sessions.
- [x] 9.4 Remove tool-calling characterization and Wikipedia smoke diagnostics
  from the model benchmark so it contains only the benchmark.
- [x] 9.5 Define a reusable direct `ToolSmokeTest` contract and add the
  Wikipedia smoke test to the Tools tab.
- [x] 9.6 Add an ephemeral device-local date, time-zone, and UTC-offset context
  to each normal Chat and Voice Chat turn without persisting it in history or
  changing the stable benchmark prompt.
