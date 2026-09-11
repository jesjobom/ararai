# Managed widgets: privacy, threats, and limits

This document defines the implemented v1 security boundary for managed-widget
persistence, authorship, scheduling, execution, and UI. Managed widgets are
app-private local data. They do not sync, export, share data, create launcher
widgets, send notifications, or gain arbitrary network access.

## Privacy and threat matrix

| Asset or boundary | Threat | Required control | Verification |
| --- | --- | --- | --- |
| Program source and revisions | Malicious or corrupt source executes outside the approved package | Store immutable revisions, verify the source digest and supported manifest/runtime versions before every activation and run, and never evaluate source during validation | Repository tasks 2.1-2.4 plus `WidgetProgramParserTest` |
| User consent and grants | A model silently expands schedule, tool, capability, retention, or action authority | The app replaces model-owned identity/control fields, derives the effective grant, shows a normalized summary/diff, and activates only after explicit confirmation | Authoring/consent tasks 6.2-7.4 |
| Provider credentials and configuration | Secrets enter prompts, source, tool arguments, logs, or executable paths | Expose only non-secret versioned tool descriptors; keep credentials, headers, endpoints, provider selection, and raw transport app-owned | Tool/authoring tasks 3.4 and 6.4 |
| Tool results and cached UI | Untrusted provider content becomes code, HTML, intents, or callbacks | Return bounded typed results and render a validated allowlisted presentation tree through fixed Compose components | `WidgetExecutionProtocolTest`, `ManagedWidgetPresentationTest`, and `ManagedWidgetPresentationComposeTest` |
| Widget observations | One widget reads or infers another widget's data | Repository methods are scoped by the caller's stable widget identity and return only typed bounded values or an indistinguishable missing result | Repository task 2.3 |
| Run history | Prompts, source, secrets, raw protocol, stack traces, or arbitrary exceptions are retained | Persist only bounded timestamps, revision, duration, planned tool IDs, controlled outcome codes, and sanitized diagnostics | Coordinator task 4.4 |
| Scheduling and workers | Duplicate work, overlapping runs, or unattended model inference | Use one unique replaceable work name and one durable lease per widget; workers load no model and immediate retry loops are forbidden | Scheduler/coordinator tasks 4.2 and 5.1-5.4, plus device task 9.4 |
| Edit, disable, and deletion races | A late run overwrites newer state or recreates deleted data | Recheck revision, consent, enablement, and deletion before committing; discard late output and delete all owned rows transactionally | Repository/coordinator tasks 2.4 and 4.2 |
| Authoring session | Prompts, algorithms, generated fragments, or model protocol leak into Chat history or durable storage | Use a fresh capture-only conversation for every staged artifact, keep validated dependencies in bounded memory only, execute JavaScript solely against synthetic fixtures, call no provider, and release all intermediate state on every exit path | Authoring tasks 11.1-11.10 |
| Authoring repair | A rejected artifact expands authority or leaks validator/provider details | Permit at most two repair generations per attempt, keep the stage schema and authority envelope frozen, and expose only a controlled failure code plus the rejected bounded artifact and relevant public contract | `WidgetAuthoringPipelineTest` and `WidgetAuthoringPipelineContractsTest` |
| External actions | A presentation opens an arbitrary URI or Android intent | Accept only a canonical HTTPS Wikipedia article URL traceable through a bounded result path, revalidate it at the UI boundary, and open it only after a user gesture | `WidgetExecutionProtocolTest`, `ManagedWidgetPresentationTest`, Compose renderer tests, and device task 9.4 |

## Checked-in v1 limits

The executable source of truth is `ManagedWidgetPolicy`; sandbox-derived values
must remain equal to, or narrower than, `WidgetRuntimePolicy`.

| Area | v1 maximum or supported set | Enforcement verification |
| --- | --- | --- |
| Managed widgets | 50 per installation | Repository boundary tests in task 2.2 |
| Periodic schedules | 1, 6, 12, or 24 hours; Android timing is inexact | `ManagedWidgetPolicyTest`, WorkManager tests in task 5.1, and device task 9.4 |
| Program revisions | 20 per widget; oldest inactive revisions evicted first | Repository retention tests in tasks 2.2-2.4 |
| Cached presentation | One active cache per widget, at most 64 KiB, revision-bound | `ManagedWidgetPolicyTest`, repository/coordinator tests in tasks 2.2 and 4.3 |
| Observations | 256 per widget, 90 days, name 64 characters, string value 4,096 characters | `ManagedWidgetPolicyTest` and repository tests in task 2.3 |
| Run history | 100 per widget, 30 days, four planned tool IDs, diagnostic 256 characters | `ManagedWidgetPolicyTest` and coordinator/repository tests in tasks 2.3 and 4.4 |
| JavaScript source | 32 KiB UTF-8 | `WidgetProgramParserTest` and `ManagedWidgetPolicyTest` |
| Proposal context | 64 KiB UTF-8 total, containing only non-secret relevant descriptors and current draft/definition | `ManagedWidgetPolicyTest` and authoring-context tests in task 6.4 |
| Authoring pipeline | Four tool calls, 16 algorithm steps, 6 KiB per generated function, 8 KiB per structured artifact, two repairs, ten generations, 90 seconds per stage, eight minutes total | `WidgetAuthoringPipelineContractsTest`, `WidgetAuthoringPipelineModelTest`, and `WidgetAuthoringPipelineTest` |
| Presentation tree | 64 KiB JSON; depth 8; 128 nodes; 32 children per container; text 2,048 characters; URL 2,048 characters | `WidgetExecutionProtocolTest`, `ManagedWidgetPolicyTest`, and renderer tests in task 8.1 |

Count and age retention rules both apply: eviction removes an entry when either
limit is exceeded. Complete widget deletion overrides retention and removes all
owned source, revisions, cache, observations, and run history.

## Regression baseline before widget behavior

- Home labels and all existing destination callbacks are locked by
  `CriticalComposeJourneysTest.homeDestinationPreservesEveryNavigationCallback`.
- Static model capability parsing and the current checked-in Chat model tool set
  are locked by `ModelConfigParserTest`.
- Normal Chat/Voice advertisement and isolation from all five ephemeral
  authoring-stage tools are locked by model/tool and Chat/Voice regression tests.

The local suite-v3 report contains only environment/model identifiers, schema
hashes, bounded timings, callback/argument-byte observations, and controlled
outcomes. It excludes every prompt, algorithm, fragment, assembled source, tool
arguments, provider content, arbitrary exception, stack trace, and secret.
- The structured `wikipedia_pages@1` and `wikipedia_on_this_day@1` contracts,
  dual model/widget eligibility, distinct selection instructions, shared model
  call budget, enablement, fixed host/path policy, bounded parsing, controlled
  failures, and transient source capture are locked by the Wikipedia knowledge,
  application-tool, and structured OpenAPI adapter tests. The legacy
  `wikipedia_search@1` regression tests remain for compatibility.
