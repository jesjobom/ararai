## MODIFIED Requirements

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

## ADDED Requirements

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
