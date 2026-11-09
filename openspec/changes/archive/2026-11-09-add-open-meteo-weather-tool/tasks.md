## 1. Contract and provider

- [x] 1.1 Define bounded `weather_forecast@1` arguments, results, failures, and attribution.
- [x] 1.2 Implement geocoding, forecast transport, normalization, time zones, and tests with local fakes.
- [x] 1.3 Require an explicit place and return `LocationRequired` without contacting a provider when absent.

## 2. Consumers and consent

- [x] 2.1 Register the tool for eligible models and independently for widgets, with disabled-by-default switches.
- [x] 2.2 Tell the Chat model to request a place when absent; disclose place-name transfer on activation.
- [x] 2.3 Require a place in new-widget authoring and remove Android location permissions and prompts.
- [x] 2.4 Add model tool adapter, catalog capability, per-turn budget, and synthesis guidance.
- [x] 2.5 Expose fixed source attribution alongside widget output; retain the existing last-result time display.

## 3. Validation and documentation

- [x] 3.1 Add focused contract, provider, missing-place, model, and widget tests.
- [x] 3.2 Update privacy/project documentation and run strict OpenSpec validation plus quality gate.
- [x] 3.3 Record real-model missing-place and named-place behavior, and saved-widget refresh evidence separately.
- [x] 3.4 Verify weather render depends on forecast measurements across distinct successful fixtures; rerun the quality gate.
- [x] 3.5 Replace weather-only model-authored presentation with an application-owned forecast card; validate the actual runtime and retry the failed presentation on device.

Named-place manual widget refresh is verified on device in
`evidence/2026-11-09-brasilia-state-qualifier-refresh.md`. Real-model missing-place
behavior is recorded in `evidence/2026-11-09-chat-missing-place.md`. An automatic
background periodic cycle remains unverified.
