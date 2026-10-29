# application-tool-platform Specification

## Purpose
Define one safe application-owned catalog and execution boundary through which
independently eligible model and widget consumers can reuse configured tools.

## Requirements

### Requirement: Registered Application Tool Contracts

Every executable application tool SHALL be registered with a stable identifier,
a supported contract version, a category, bounded input and output schemas, and
an explicit non-empty set of eligible consumers. Registration SHALL reject
duplicate identifier/version pairs and internally inconsistent contracts before
the tool can be discovered or invoked.

#### Scenario: Register one valid tool contract

- **WHEN** the application registers a tool with a unique identifier/version,
  valid bounded schemas, and at least one eligible consumer
- **THEN** eligible application consumers can discover its non-secret metadata
- **AND** registration does not execute the tool.

#### Scenario: Reject ambiguous registration

- **WHEN** two registrations use the same tool identifier and contract version
  or a registration has no eligible consumer
- **THEN** registry construction fails deterministically
- **AND** neither ambiguous registration is exposed for execution.

#### Scenario: Resolve an unsupported contract version

- **WHEN** a consumer requests a registered tool identifier with a contract
  version the application does not support
- **THEN** execution returns a controlled incompatible-version failure
- **AND** the application does not silently invoke another version.

### Requirement: Independent Consumer Eligibility

Tool eligibility for `MODEL` and `WIDGET` consumers SHALL be declared and
resolved independently. Model compatibility SHALL affect only `MODEL`
eligibility and SHALL NOT make a tool available or unavailable to `WIDGET`
execution.

#### Scenario: Resolve a model-only tool

- **GIVEN** a registered enabled tool declares only `MODEL` eligibility
- **WHEN** model and widget consumers resolve that tool
- **THEN** the model consumer may use it only when the selected model also has
  verified capability
- **AND** the widget consumer receives a controlled ineligible-consumer result.

#### Scenario: Resolve a widget-only tool

- **GIVEN** a registered enabled and operationally ready tool declares only
  `WIDGET` eligibility
- **WHEN** model and widget consumers resolve that tool
- **THEN** the widget consumer can resolve it without a selected or loaded model
- **AND** the tool is not advertised to any model.

#### Scenario: Resolve a dual-consumer tool

- **GIVEN** a registered enabled and operationally ready tool declares both
  `MODEL` and `WIDGET` eligibility
- **WHEN** each consumer resolves the tool
- **THEN** each consumer applies its own eligibility and execution policy
- **AND** both reach the same application-owned tool implementation.

### Requirement: User-Controlled Tool Readiness

The application SHALL distinguish a registered tool, consumer-specific user enablement, and operational readiness. MODEL/Chat and WIDGET enablement SHALL be persisted independently. Provider credentials and verification state MAY be shared by tools and eligible consumers using the same provider, but they SHALL NOT themselves enable a consumer or grant a widget revision permission. A tool requiring credentials or prior verification SHALL NOT become ready until those requirements are satisfied. A tool disabled for the requesting consumer SHALL NOT execute for that consumer, regardless of the other consumer's state.

#### Scenario: Keep a disabled tool unavailable

- **GIVEN** a registered tool is disabled for one consumer
- **WHEN** that consumer attempts to resolve or invoke it
- **THEN** the application returns a controlled disabled result and performs no tool execution
- **AND** the other consumer's enablement is unchanged.

#### Scenario: Require configured credentials

- **GIVEN** an enabled tool requires a provider credential
- **AND** the credential is absent, unreadable, removed, or not successfully verified under the provider's existing policy
- **WHEN** any consumer attempts to invoke the tool
- **THEN** the application returns a controlled not-configured result
- **AND** does not contact the provider for that invocation.

#### Scenario: Share a verified provider configuration

- **GIVEN** a provider is used by tools eligible for both consumers
- **WHEN** the user configures and verifies it from either consumer's tool surface
- **THEN** both surfaces observe the same provider setup state
- **AND** each consumer still requires its independent enablement and eligibility checks.

#### Scenario: Disable a configured tool

- **GIVEN** a tool is configured and operationally ready
- **WHEN** the user disables it for one consumer
- **THEN** future resolution for that consumer excludes it
- **AND** the other consumer's enablement and the stored provider credential remain unchanged.

#### Scenario: Migrate existing shared enablement

- **WHEN** the application first introduces independent widget enablement on an existing installation
- **THEN** it preserves the previously effective Chat and widget availability before any new user change
- **AND** later changes to one consumer's enablement do not silently change the other.

### Requirement: Validated Shared Tool Execution

Every tool invocation SHALL pass through one application-owned dispatch
boundary that validates tool identity, contract version, consumer eligibility,
user enablement, operational readiness, bounded arguments, cancellation, and
the tool's execution policy before calling the registered implementation. The
boundary SHALL return a bounded structured success or a controlled failure and
SHALL NOT interpret generated executable code.

#### Scenario: Execute a valid invocation

- **GIVEN** a registered tool is enabled, operationally ready, and eligible for
  the requesting consumer
- **WHEN** the consumer supplies arguments valid for the requested contract
  version
- **THEN** the application executes the registered implementation once
- **AND** returns its validated structured result through the consumer adapter.

#### Scenario: Reject malformed arguments before execution

- **WHEN** invocation arguments contain missing, additional, oversized, or
  incorrectly typed values relative to the registered input schema
- **THEN** the application returns a controlled invalid-arguments result
- **AND** does not call the tool implementation.

#### Scenario: Cancel shared execution

- **WHEN** the owning consumer cancels an in-flight tool invocation
- **THEN** cancellation propagates to the shared execution boundary
- **AND** a late result is discarded
- **AND** no automatic retry is introduced by the shared boundary.

#### Scenario: Keep transport control application-owned

- **WHEN** a model or widget invokes a registered network tool
- **THEN** it can provide only declared semantic arguments
- **AND** cannot provide an endpoint, arbitrary URL, header, credential,
  timeout, provider implementation, or executable callback.

### Requirement: Private Tool Configuration

Tool credentials and private provider configuration SHALL be supplied to the
registered implementation only through application-owned configuration. They
MUST NOT appear in public tool metadata, model-visible schemas or context,
widget invocations or future persisted widget plans, tool results, logs,
diagnostics, analytics, backups, or exports.

#### Scenario: Describe a credentialed tool

- **GIVEN** a registered tool uses a readable provider credential
- **WHEN** a model or widget consumer discovers its tool contract
- **THEN** the consumer receives only non-secret metadata and semantic schemas
- **AND** cannot retrieve the credential value or its storage representation.

#### Scenario: Execute with an application-owned credential

- **WHEN** a valid credentialed invocation reaches the registered implementation
- **THEN** the application resolves the credential outside consumer arguments
- **AND** redacts the credential from every result and diagnostic path.

### Requirement: Widget Tool Execution Gateway

The application SHALL expose a widget-domain execution gateway that invokes
registered `WIDGET`-eligible tools directly through the shared dispatch
boundary. The gateway SHALL NOT load or prompt a language model and SHALL NOT
itself generate, render, persist, manage, or schedule widgets.

#### Scenario: Execute a widget-eligible tool directly

- **GIVEN** a bounded widget invocation references an enabled, ready,
  `WIDGET`-eligible tool and supported contract version
- **WHEN** the widget gateway executes the invocation
- **THEN** it dispatches the validated semantic arguments to the registered
  implementation
- **AND** returns a bounded structured result without model inference.

#### Scenario: Reject a model-only tool from the widget gateway

- **GIVEN** a registered tool does not declare `WIDGET` eligibility
- **WHEN** the widget gateway receives an invocation for it
- **THEN** the gateway returns a controlled ineligible-consumer failure
- **AND** does not call the implementation or a model-facing adapter.

#### Scenario: Keep future widget lifecycle out of the gateway

- **WHEN** the widget gateway completes or fails an invocation
- **THEN** it returns the result to its caller
- **AND** does not create a widget, persist a widget definition, register a
  background worker, or choose another execution time.

### Requirement: Structured Wikipedia pages tool

The application SHALL register a stable versioned `wikipedia_pages` tool whose
bounded semantic input contains only a search query and supported Wikipedia
language and whose canonical result contains a bounded list of page title,
plain-text extract, canonical HTTPS URL, language, and retrieval time. The tool
SHALL be explicitly eligible for `MODEL` and `WIDGET`, reuse the
application-owned Wikipedia transport and validation policy, and remain subject
to user enablement, verified model capability, and shared dispatch.

#### Scenario: Search Wikipedia for a model or widget

- **GIVEN** the Wikipedia tool is enabled and operationally ready
- **WHEN** an eligible model or widget submits a valid bounded query and language to
  `wikipedia_pages` through the shared dispatcher
- **THEN** the application contacts only the validated language-specific
  Wikipedia API host
- **AND** returns only bounded validated page fields or a controlled failure.

#### Scenario: Reject a widget-controlled transport

- **WHEN** a widget attempts to supply an endpoint, arbitrary URL, header,
  credential, provider, redirect policy, timeout, or executable callback
- **THEN** the request is rejected before network access
- **AND** the application-owned Wikipedia transport remains authoritative.

#### Scenario: Advertise structured Wikipedia pages to verified models

- **GIVEN** Wikipedia is enabled and the selected model declares
  `wikipedia_pages`
- **WHEN** normal Chat or Voice Chat resolves model-callable tools
- **THEN** `wikipedia_pages` is advertised with direct stable page-lookup
  guidance
- **AND** its structured untrusted result and canonical sources are available to
  the model adapter.

#### Scenario: Return untrusted external content

- **WHEN** Wikipedia returns page titles or extracts containing markup,
  instructions, oversized fields, malformed Unicode, or noncanonical links
- **THEN** the tool normalizes and bounds valid plain-text fields and canonical
  links or returns a controlled malformed/unavailable failure
- **AND** no returned text is interpreted as code or application instruction.

### Requirement: Structured Wikipedia events-by-date tool

The application SHALL register a stable versioned `wikipedia_on_this_day` tool
eligible for `MODEL` and `WIDGET`. Its input SHALL contain only numeric month,
numeric day, and a supported Wikipedia language. Its canonical result SHALL
contain a bounded list of historical event year, plain-text description,
related page title, canonical HTTPS URL, language, and retrieval time from a
fixed application-owned official Wikipedia on-this-day endpoint.

#### Scenario: Retrieve actual events for a calendar day

- **GIVEN** Wikipedia is enabled and operationally ready
- **WHEN** an eligible model or widget requests a valid month, day, and language
- **THEN** the application contacts only the validated language-specific
  Wikipedia on-this-day path
- **AND** returns bounded event items rather than a date-index page.

#### Scenario: Reject invalid dates and transport control

- **WHEN** a request supplies an impossible month/day, extra endpoint, arbitrary
  URL, header, credential, redirect policy, timeout, or executable callback
- **THEN** it is rejected before network access
- **AND** no request-controlled transport option is honored.

#### Scenario: Select the correct Wikipedia tool

- **GIVEN** both structured Wikipedia tools are advertised to a verified model
- **WHEN** the user asks for an event on a calendar date
- **THEN** model-visible descriptions and system guidance direct the model to
  `wikipedia_on_this_day`
- **AND** direct biographies, concepts, works, places, or other stable page
  lookups are directed to `wikipedia_pages`.

#### Scenario: Share one bounded Wikipedia turn budget

- **WHEN** a model can call both structured Wikipedia tools in one user turn
- **THEN** their combined calls are limited to the existing Wikipedia per-turn
  maximum
- **AND** successful canonical sources remain attributable to the tool result.

### Requirement: Contextual Provider Configuration

A credentialed tool SHALL offer a provider configuration action from each tool-management surface where the provider is relevant. Simple token configuration SHALL be possible in a dismissible, accessible dialog with masked input, verification, and explicit credential removal. The configuration UI SHALL be provider-specific rather than requiring every future provider to use a token or username/password form. A failed verification SHALL not replace a previously valid credential. Provider secrets SHALL remain subject to Private Tool Configuration regardless of the UI entry point.

#### Scenario: Configure a token from widget tools

- **WHEN** the user opens the configuration action for a credentialed widget tool and submits a valid token
- **THEN** the provider verifies and stores it using application-owned configuration
- **AND** the dialog returns to widget tools with the updated provider state
- **AND** the token value is not displayed after saving.

#### Scenario: Verification fails

- **GIVEN** a provider already has a valid stored credential
- **WHEN** verification of a replacement credential fails
- **THEN** the existing credential remains valid and stored
- **AND** the user receives a bounded failure indication without leaking either credential.

#### Scenario: Remove a credential

- **WHEN** the user explicitly removes a provider credential from either tool surface
- **THEN** that provider becomes unready for every consumer that relies on it
- **AND** independent consumer enablement preferences and widget revision permissions remain unchanged.
