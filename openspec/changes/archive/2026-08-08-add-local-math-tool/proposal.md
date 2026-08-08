# Change: Add local math tool

## Why

Language-model token generation is not a reliable arithmetic engine. ArarAI can
improve mathematical answers by allowing eligible local models to request a
bounded calculation from an application-owned local engine and use the returned
value when composing the final response. The capability must preserve the app's
local-first privacy model and must not interpret expressions through executable
code.

The expression-engine evaluation selected EvalEx 3.7.0 behind an app-owned,
strictly allowlisted adapter. The research found that core decimal arithmetic is
BigDecimal-based while transcendental and fractional-power paths use double, so
the product contract distinguishes exact, rounded, and approximate results. The
larger EvalEx-big-math extension is deferred.

## What Changes

- Use EvalEx 3.7.0 after a recorded comparison of maintained math-expression
  libraries and a minimal app-owned grammar against product, security, Android,
  licensing, precision, dependency, and measured size criteria.
- Generalize the application tool boundary so local deterministic tools are not
  represented as external knowledge providers.
- Add an optional local `calculator` tool for models with individually verified
  structured tool-calling support.
- Parse and evaluate bounded mathematical expressions without network access,
  dynamic code evaluation, scripts, reflection, or arbitrary application access.
- Return structured success or controlled failure results to the model, with
  documented numerical and formatting semantics.
- Add local tool enablement and privacy/capability disclosure to Assistant
  configuration while preserving normal generation for ineligible models.
- Exclude function plotting, chart rendering, symbolic proof, arbitrary equation
  solving, and general computer-algebra behavior from this change.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: tool contracts and LiteRT-LM registration/execution, Assistant
  configuration, local calculation engine, generation events/UI, and tests
- Dependencies: subject to the recorded library evaluation and repository supply
  chain policy
- Privacy: calculation inputs and results remain on-device and require no network
  permission or provider credential
