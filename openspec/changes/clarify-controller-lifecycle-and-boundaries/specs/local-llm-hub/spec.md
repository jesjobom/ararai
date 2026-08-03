## ADDED Requirements

### Requirement: Explicit Chat controller lifecycle

The application SHALL give each Chat controller an explicit, idempotent disposal
boundary that cancels work owned by that controller without cancelling an
externally supplied parent scope.

#### Scenario: Chat owner leaves composition

- **WHEN** the Compose owner disposes the Chat controller
- **THEN** active generation and delayed controller work are cancelled
- **AND** subsequent controller commands do not start new work
- **AND** repeated disposal has no additional effect

### Requirement: Centralized application controller ownership

The application SHALL construct the shared local model runtime and its Chat,
Voice Chat, benchmark, and diagnostic controllers in a dedicated composition
root with one explicit disposal boundary.

#### Scenario: Application composition is removed

- **WHEN** the application controller owner leaves composition
- **THEN** its controllers and shared local model runtime are closed
- **AND** normal Chat and Voice Chat used the same conversation selection,
  coordinator, store, and engine for the owner's lifetime

### Requirement: Focused LiteRT lifecycle policy

The LiteRT integration SHALL keep generic resource ownership and pure conversation
reuse decisions separate from Android SDK-specific inference callbacks.

#### Scenario: LiteRT session replaces or cancels a conversation

- **WHEN** a retained conversation is incompatible or an active generation is cancelled
- **THEN** the focused ownership policy disposes each claimed resource at most once
- **AND** the reuse policy accepts only a matching non-null session and transcript
