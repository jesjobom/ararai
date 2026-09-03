## Purpose

Define one safe application-owned catalog and execution boundary through which
independently eligible model and widget consumers can reuse configured tools.

## ADDED Requirements

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

The application SHALL distinguish a registered tool from a user-enabled tool
and an operationally ready tool. User enablement SHALL be persisted locally,
and a tool requiring credentials or prior verification SHALL NOT become ready
until those requirements are satisfied.

#### Scenario: Keep a disabled tool unavailable

- **GIVEN** a tool is registered but disabled by the user
- **WHEN** any consumer attempts to resolve or invoke it
- **THEN** the application returns a controlled disabled result
- **AND** performs no local computation or network request for that invocation.

#### Scenario: Require configured credentials

- **GIVEN** an enabled tool requires a provider credential
- **AND** the credential is absent, unreadable, removed, or not successfully
  verified under the provider's existing policy
- **WHEN** any consumer attempts to invoke the tool
- **THEN** the application returns a controlled not-configured result
- **AND** does not contact the provider.

#### Scenario: Disable a configured tool

- **GIVEN** a tool is configured and operationally ready
- **WHEN** the user disables it
- **THEN** future model and widget resolution excludes it
- **AND** its credential may remain stored according to the tool's existing
  credential policy.

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
