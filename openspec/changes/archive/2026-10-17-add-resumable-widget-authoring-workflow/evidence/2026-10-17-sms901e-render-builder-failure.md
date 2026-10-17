# SM-S901E resumed authoring and render failure — 2026-10-17

The connected device retained one E4B authoring session at `render` with
`AwaitingRetry` status. Feasibility, algorithm, the selected tool call, and
plan had accepted checkpoints. Three interrupted attempts at algorithm, call,
and render recorded `RuntimeUnavailable` after the user closed the app; the
accepted upstream checkpoints survived subsequent process recreation.

The initial render revision had two controlled `InvalidRenderExecution`
failures. The user reprocessed and accepted plan revision 2, leaving render
to be generated again. Three render attempts on revision 2 then failed with
the same controlled code. Nothing was confirmed or activated from this
session. No prompt, generated source, tool result, or widget identifier is
retained in this evidence note.

The rejected render code treated `runtime.card()` as a mutable builder with
`addText`/`addLinkFor`, and one attempt called `outcomes.getArray`. Those
methods do not exist: `runtime.card` takes one string or array argument and
returns a complete node; arrays are read through
`runtime.getArray(outcomes, alias, path)`. The model-facing Render context,
objective, and repair instructions now give that exact call shape and rule out
the nonexistent methods. A Node runtime regression covers both the failing
assumptions and the correct title/body/link form.

The physical observation also supplies practical evidence for resumable task
7.3: accepted stages survived interruption; failure and Retry were visible;
reprocessing plan started a fresh render revision.
The stage-runner tests assert model unload after successful, failed, and
cancelled attempts; process death necessarily removed the earlier in-process
model instance. No separate device heap measurement is claimed.

Targeted Android tests and all 13 Node runtime-helper tests passed. The full
`spotlessCheck detekt testDebugUnitTest lintDebug assembleDebug` gate passed;
strict OpenSpec validation and `git diff --check` passed. Debug APK SHA-256
`c7ce876e6236e27c28e27186c43a5ec882af8006527bf01fdb01ec2b15a36284`
installed successfully on SM-S901E. Android reports version `202610171006`.
Before and after installation the session remained `AwaitingRetry` at render
with 13 attempts and the same four checkpoint digests; all 12 saved widgets
remained. No model-dependent retest was run by the agent.

The user then retried on the installed version. A read-only database check
afterward found no active authoring session, 13 saved widgets (one more than
before the retry), and a new `Ready` widget created at 14:46:29 UTC. Its
initial and subsequent runs both recorded `Success`; the persisted card has
separate event title and body text plus a Wikipedia HTTPS link. The agent did
not initiate this model-dependent retest.
