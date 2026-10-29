# Design: tool management by consumer, provider configuration shared

## Context and decision

The application already has distinct Chat and widget registries with consumer eligibility checked at dispatch. The missing boundary is user-facing management: Chat exposes tool controls, while widgets expose only tool IDs in capability/detail views. Wikipedia widget readiness currently follows the Assistant preference. Provider authentication is a different concern from permission to use a tool in either consumer.

Use three layers:

1. **Provider configuration:** one application-owned credential/verification state per provider, shared by all eligible consumers of that provider. Preserve the current protected local credential storage and never copy secrets into a widget definition or model context.
2. **Consumer enablement:** independent persisted Chat and widget choices. Effective availability also requires registration, consumer eligibility, provider readiness, and (for Chat) verified model capability. A tool not eligible for widgets is shown as unavailable, not silently offered for enablement.
3. **Widget revision permission:** the confirmed revision remains an additional allowlist at execution. Global widget enablement is a prerequisite, never a grant to a widget.

Do not equate a provider with a tool: multiple contracts may share one provider configuration, and one tool category may offer multiple providers. A single provider configuration action must open the same underlying state from either context.

## Navigation and interaction

- Home **Widgets** opens the existing managed-widget list directly. Keep **Create widget** and the empty state there; place an accessible tools icon at the top-right of the list app bar. The icon opens widget tools, and Back returns to the list. Android launcher entry to a specific widget continues directly to its detail.
- Assistant configuration retains the **Tools** tab, explicitly scoped to Chat/model use. Each surface lists its consumer's supported tools with understandable availability reasons. Show configured provider status, never a stored secret.
- A tool requiring setup has a configuration icon/action. For current token providers, open a dialog with masked input, verify/save, cancel, and remove credential. Failed verification leaves the prior valid configuration intact unless the user explicitly removes it. Close/return to the originating tool surface, and refresh status in both surfaces after a successful change.
- Configuration is provider-specific rather than a universal token or username/password form. A future multi-step provider may use a dedicated flow from the same action. Avoid inventing storage of arbitrary username/password credentials in this change.
- A blocked-tool action in widget authoring opens widget tools, with a return path to the authoring state; it must not redirect to Chat tools.

## Compatibility and migration

Seed the new widget enablement choice from the existing effective widget state on first migration (not a blanket default). Keep the existing Chat choice unchanged. After migration, changing either choice must not mutate the other. Preserve provider credentials and existing widget revisions. At execution, continue to reject tools absent from a confirmed revision even if the provider and widget consumer are ready.

Web search remains Chat-only until separately approved for widget eligibility and its network/runtime policy is defined. The shared credential design must support that later without promising widget access now.

## Validation

Cover migration from both old Wikipedia switch values; independent toggles; shared credential status and failed verification; disabled/unready/ineligible tool presentation; authoring return navigation; existing revision allowlist enforcement; and launcher deep links. Run the repository quality gate. A real device check should confirm dialogs and navigation without running destructive connected tests on the retained phone.
