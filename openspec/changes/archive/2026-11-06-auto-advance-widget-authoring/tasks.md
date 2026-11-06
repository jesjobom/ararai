## Implementation

- [x] Chain successful stage attempts at application scope with stale-revision and configuration checks.
- [x] Stop on failed or terminal stages and retain explicit final confirmation.
- [x] Add deterministic tests for automatic progression and stopping.
- [x] Update user guidance and run the repository quality gate.
- [x] Include the stage-attempt number in retry prompts; verify successive retries and revalidate.
- [x] Retain latest bounded actionable repair evidence across retries, with input-budget fallback; update tests and documentation.
