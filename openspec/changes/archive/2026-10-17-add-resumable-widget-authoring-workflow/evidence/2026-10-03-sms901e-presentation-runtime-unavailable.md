# SM-S901E presentation `RuntimeUnavailable`

## Evidence

- Device: Samsung SM-S901E
- APK version: `202610032204`
- Sanitized log: `2026-10-03-sms901e-presentation-runtime-unavailable.log`
- Log SHA-256: `300526e8fbb8b4b2c92830b9f58b58b8d0a20b07aaf3d40385c3fa574b1fd02a`

## Findings

All three render attempts passed recovery and loaded the LiteRT-LM engine, then
closed it without a `generation_started` event or captured artifact. The exact
render objective for the accepted `fetch_wikipedia_events` alias was 520
characters, exceeding the 512-character request-objective limit. Construction
therefore threw after model load, and the stage runner's generic exception path
persisted the misleading `RuntimeUnavailable` code.

The same run also contains an independent
`ForegroundServiceDidNotStartInTimeException` immediately after the local plan.
The durable state crossed from active to review before the pending foreground
refresh reached `WidgetAuthoringService`, so the service stopped without first
acknowledging Android's foreground-start contract.

## Required regression coverage

- Keep the render objective within 512 characters for the physical-test alias
  and the maximum valid 32-character algorithm alias.
- Carry exact presentation node shapes in bounded render context.
- Construct the complete stage request before recovery/model load and classify
  local construction failure as invalid schema.
- Give foreground refresh an explicit action and acknowledge it before keeping,
  detaching, or removing the notification from the latest durable state.
