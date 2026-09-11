# add-managed-llm-authored-widgets

Add managed widgets authored and edited by the local LLM, using the sandboxed widget runtime.

## Foundation checkpoint

Source implementation may start under the explicit deferred-device-validation
gate recorded by this change. The foundation currently supplies:

- widget manifest schema 1 and widget API 1;
- vendored QuickJS `2026-06-04`;
- maximum 8 MiB QuickJS heap, 512 KiB stack, 250 ms execution, four tool calls,
  32 KiB source, and 64 KiB JSON/output policy limits;
- passing host-native, JVM, lint, debug, optimized instrumentation assembly,
  release-candidate/R8, dependency, license, and strict OpenSpec gates;
- a release-candidate-only manual validator for collecting the remaining
  physical arm64 isolation, termination, cancellation, and memory evidence.

The deferred physical gate passed on 2026-09-05 with 12/12 cases on an arm64
Samsung SM-S942W running Android 16/API 36. The canonical report is stored at
`../archive/2026-09-05-add-sandboxed-javascript-widget-runtime/evidence/2026-09-05-samsung-sm-s942w.json`.
The foundation is now accepted as an implementation dependency; its separate
archival remains an explicit delivery action.
