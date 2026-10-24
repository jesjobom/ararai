# Compact widget authoring stages

## Why

The staged authoring screen renders every captured artifact inline, making a
workflow with several attempts hard to scan. The manual retry cap also blocks
recovery from a model that requires more than three user-authorized attempts.

## What changes

- Show a concise status, latest outcome, elapsed time, and attempt count per
  stage; keep the complete attempt history available on expansion.
- Place contextual actions below each stage and label both starting a pending
  stage and accepting a reviewed result **Advance**.
- Permit reprocessing a successful candidate before acceptance as well as an
  accepted checkpoint.
- Remove the cumulative cap on explicit manual retries. Each action still
  starts at most one generation; bounded automatic repair/diagnostic policies
  remain unchanged.
