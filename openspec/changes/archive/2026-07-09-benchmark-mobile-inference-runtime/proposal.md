# Benchmark Mobile Inference Runtime

## Why

ArarAI now exposes multiple configured local models, but model usability on a
physical Android device depends on runtime performance. A larger model can be
available and still feel unusable if load time, first-token latency, or
token-throughput are too slow.

The app needs a dedicated benchmark flow so performance measurements are
repeatable and separate from normal chat UX.

## What Changes

- Add a new home/menu entry that opens a benchmark screen.
- Add a dedicated benchmark screen for the selected configured model.
- Use stable benchmark parameters instead of free-form chat settings.
- Measure model load time, time to first token, generated token count, total
  generation time, and tokens per second.
- Show the active model, configured limits, backend label, benchmark status, and
  latest result.
- Keep benchmark execution behind the existing local engine boundary so future
  runtime backends can reuse the same product flow.

## Impact

- Affects home navigation, benchmark state management, and local engine
  observability.
- Does not enable GPU acceleration yet.
- Does not persist benchmark history yet.
- Does not replace manual physical-device validation; it creates a repeatable
  in-app measurement path for that validation.
