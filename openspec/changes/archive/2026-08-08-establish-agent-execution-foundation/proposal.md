# Change: Establish agent execution foundation

## Why

ArarAI is increasingly maintained through coding agents, but the repository does
not provide an automatically discovered operating guide. The Voice Chat
controller is also a critical orchestration boundary without focused unit
characterization, so agents cannot safely change its capture, persistence,
generation, cancellation, and cleanup behavior.

## What Changes

- Add a repository-level `AGENTS.md` that points agents to the canonical OpenSpec
  contract, the common quality gate, and the limits of automated validation.
- Add deterministic JVM/Robolectric characterization tests for
  `VoiceChatViewModel` using local fakes only.
- Cover model preparation, completed turns, failure/cancellation cleanup, and
  persisted conversation behavior without real microphones, models, network, or
  text-to-speech engines.
- Keep production behavior unchanged; defects discovered by characterization are
  recorded for a separate OpenSpec change.

## Impact

- Affected spec: `local-llm-hub`, `voice-chat`
- Affected code: repository operating guidance and Voice Chat test suite
- Production behavior: unchanged

