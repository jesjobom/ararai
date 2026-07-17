## Context

The README and project context still present the first text-only llama.cpp MVP
as the current product. Since that slice, ArarAI gained a catalog, model
selection, LiteRT-LM, persistent multimodal Chat, diagnostics, privacy rules,
and automated/device quality gates. The consolidated spec and source contain
the current evidence, but onboarding documentation points maintainers toward an
archived path and obsolete exclusions.

## Goals / Non-Goals

**Goals:**

- Describe only capabilities and architecture supported by current source,
  configuration, tests, and the consolidated spec.
- Separate the historical MVP from current product constraints.
- Give maintainers executable build, validation, device, and APK-handoff paths.
- Define source precedence and a repeatable documentation review trigger.

**Non-Goals:**

- Change application behavior or consolidate unrelated OpenSpec requirements.
- Claim that CI proves real GPU, model, memory, thermal, or device behavior.
- Turn the README into an exhaustive requirements or API reference.

## Decisions

### Keep the README operational and the project context architectural

The README provides a capability overview, toolchain, commands, privacy policy,
and planning entry points. `openspec/project.md` records architecture,
boundaries, historical context, validation policy, and known constraints. This
avoids duplicating the full consolidated specification in both documents.

An alternative was to make the README the complete product specification. It
was rejected because duplicated normative requirements drift quickly and make
precedence ambiguous.

### Use explicit source precedence

The consolidated spec is the product contract; an approved active change is its
pending delta; source/configuration/tests provide exact implementation evidence;
README and project context summarize them. Archived changes remain historical.
This resolves conflicts without implying that documentation can override an
approved requirement or actual build configuration.

An alternative was to declare source code the only truth. It was rejected
because implemented behavior can be incomplete or accidental, while OpenSpec
captures intended product contracts.

### Bound device-dependent claims

Documentation names physical-device checks for real inference, GPU/backend,
memory, thermal, permissions, and lifecycle instead of treating successful
compilation as proof. Features gated by catalog capability metadata are
described conditionally.

An alternative was to list every configured model as fully supported. It was
rejected because catalog presence and a compiled adapter do not prove reliable
behavior on every target device.

### Review documentation at change completion

Any change affecting capabilities, architecture, setup, validation, privacy, or
supported workflows must review the README and project context before completion
or archive. Changes without such impact need no documentation churn.

## Risks / Trade-offs

- [Capability descriptions drift again] -> Use the explicit completion/archive
  review trigger and keep normative detail in OpenSpec.
- [README overstates runtime support] -> Use conditional capability language and
  link physical-device validation requirements.
- [Version values become stale] -> Point to Gradle and catalog resources as the
  exact implementation sources while keeping the current baseline visible.
- [Historical decisions are lost] -> Preserve them in a labeled historical
  section and archived OpenSpec changes.

## Migration Plan

Replace the stale README and project context, validate every path and command,
run the common quality gate, then validate this change strictly. Rollback is a
documentation-only revert; no application data or runtime migration exists.

## Open Questions

None for this change. Future capabilities must be introduced through their own
OpenSpec changes before being documented as implemented.
