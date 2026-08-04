## 1. Research and specification gate

- [ ] 1.1 Define a representative evaluator corpus covering supported arithmetic, candidate advanced operations, precedence, precision, locale, domain errors, adversarial inputs, complexity, cancellation, and performance.
- [ ] 1.2 Compare maintained expression engines and a minimal app-owned grammar against license, Android API 28 compatibility, maintenance/supply-chain posture, transitive graph, measured binary-size impact, numerical semantics, resource control, performance, and arbitrary-execution risk.
- [ ] 1.3 Record the selected approach, rejected alternatives, evidence, supported grammar, numerical/rounding policy, hard limits, dependency impact, and residual risks in a repository-local decision artifact.
- [ ] 1.4 Revise this proposal, design, spec delta, and remaining tasks for every material restriction or safe capability found by the research; run strict OpenSpec validation before adding a production dependency or calculator code.

## 2. Generic tool boundary

- [ ] 2.1 Establish failing compatibility and category tests around current Wikipedia/web-search registration, execution, failures, provenance, and per-turn limits.
- [ ] 2.2 Introduce the runtime-neutral application-tool contract and migrate existing knowledge tools without behavior changes.
- [ ] 2.3 Extend model capability metadata and effective tool-set invalidation for individually verified calculator support.

## 3. Local calculation engine

- [ ] 3.1 Add the selected dependency with locking and verification metadata, or implement the approved minimal grammar, as determined by the research gate.
- [ ] 3.2 Implement bounded parsing/evaluation behind an app-owned interface with stable structured success and failure results.
- [ ] 3.3 Enforce the researched input, complexity, execution, cancellation, numerical, and locale-independent formatting policies.
- [ ] 3.4 Verify the engine against the evaluator corpus, including malformed/adversarial input, undefined/non-finite outcomes, repeatability, and concurrency.

## 4. Structured model integration

- [ ] 4.1 Register `calculator` only for enabled, explicitly capable models and add the local structured execution bridge.
- [ ] 4.2 Integrate calculator start, completion, failure, cancellation, and per-turn call limits with normal Chat and Voice Chat generation lifecycles.
- [ ] 4.3 Preserve canonical conversation reconstruction without persisting raw calculator protocol or intermediate values as visible messages.
- [ ] 4.4 Add deterministic end-to-end controller tests for calculation-assisted answers and normal answers that do not invoke the tool.

## 5. Configuration and disclosure

- [ ] 5.1 Add disabled-by-default calculator enablement to Assistant configuration and distinguish local computation from network tools.
- [ ] 5.2 Show capability gating, supported-scope/error guidance, and on-device privacy disclosure.
- [ ] 5.3 Update project context and user documentation for the implemented grammar, numerical guarantees, limits, and validation boundary.

## 6. Validation

- [ ] 6.1 Run focused evaluator, tool-regression, Chat, Voice Chat, persistence, and configuration tests.
- [ ] 6.2 Run the complete project quality gate and strict OpenSpec validation.
- [ ] 6.3 Record physical-device evidence for LiteRT-LM tool selection/final synthesis, cancellation, responsiveness, memory, and installed-size impact; leave unexecuted checks explicit.
