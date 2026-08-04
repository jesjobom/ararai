# Change: Add local math tool

## Why

Language-model token generation is not a reliable arithmetic engine. ArarAI can
improve mathematical answers by allowing eligible local models to request a
bounded calculation from an application-owned local engine and use the returned
value when composing the final response. The capability must preserve the app's
local-first privacy model and must not interpret expressions through executable
code.

The exact expression engine is deliberately not selected in this proposal. A
time-boxed library evaluation is the first implementation gate because Android
compatibility, license, binary size, numerical semantics, supported functions,
maintenance quality, and cancellation behavior may constrain or expand the
safe product contract.

## What Changes

- Evaluate maintained math-expression libraries against explicit product,
  security, Android, licensing, precision, and size criteria before selecting an
  engine or adding a dependency.
- Record the evaluation and revise this change's proposal, design, requirements,
  and tasks when its findings materially affect scope; implementation does not
  begin until those documents are internally consistent.
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
