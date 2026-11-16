## MODIFIED Requirements

### Requirement: Configured Chat System Prompt

The app SHALL compose an effective system instruction from app-owned invariants
a bounded locally persisted user instruction selected independently for
normal Chat or Voice Chat, and bounded shared memory available across both modes
and all conversation sessions.

#### Scenario: Initialize instruction preferences

- **GIVEN** the user has not customized either interaction mode
- **WHEN** instruction preferences are loaded
- **THEN** normal Chat uses its checked-in default user instruction
- **AND** Voice Chat uses its checked-in default user instruction
- **AND** app-owned invariant instructions remain present and non-editable.

#### Scenario: Build prompt with configured system prompt

- **GIVEN** the effective configured system instruction is present
- **AND** a persisted conversation has previous eligible messages
- **WHEN** either Chat screen submits a new turn that requires context
  initialization
- **THEN** generation receives the effective system instruction
- **AND** recent eligible conversation history
- **AND** the new user message
- **AND** a compatible incremental native continuation does not prefill that
  unchanged history again.

#### Scenario: Build a normal-Chat prompt

- **GIVEN** normal Chat has a persisted user instruction
- **WHEN** normal Chat submits a turn that requires context initialization
- **THEN** generation receives the app-owned invariant instruction
- **AND** the normal-Chat user instruction
- **AND** the same ordered shared-memory entries used by Voice Chat
- **AND** recent eligible canonical conversation history
- **AND** the new user message.

#### Scenario: Build a Voice-Chat prompt

- **GIVEN** Voice Chat has a persisted user instruction
- **WHEN** Voice Chat submits a turn that requires context initialization
- **THEN** generation receives the app-owned invariant instruction
- **AND** the Voice-Chat user instruction
- **AND** the same ordered shared-memory entries used by normal Chat
- **AND** recent eligible canonical conversation history
- **AND** the new user turn.

#### Scenario: Add current temporal context to a conversation turn

- **GIVEN** the device has a current local date and configured time zone
- **WHEN** normal Chat or Voice Chat builds a generation request
- **THEN** the effective system instruction contains the current local date,
  time-zone identifier, and UTC offset
- **AND** the temporal context is regenerated for that turn
- **AND** it appears only once in the projected prompt
- **AND** it is not persisted as canonical conversation history.

#### Scenario: Keep editable context within a shared budget

- **GIVEN** the user's mode-specific prompts and ordered memory entries
- **WHEN** either mode constructs its effective instruction
- **THEN** the active prompt and shared memory fit the shared editable-context
  allowance, independently of the selected model's larger total context window
- **AND** neither mode's prompt is rotated or silently truncated
- **AND** the allowance is enforced against the longer of the two prompts so
  the same memory fits with either mode.

#### Scenario: Restore an instruction default

- **GIVEN** the user customized one interaction mode's instruction
- **WHEN** the user restores that mode's default
- **THEN** only that editable instruction returns to its checked-in default
- **AND** the other interaction mode remains unchanged
- **AND** canonical conversation history remains unchanged.

#### Scenario: Add shared memory to later sessions

- **GIVEN** the user explicitly saved a memory entry
- **WHEN** a later Chat or Voice Chat turn starts in any session
- **THEN** the entry is included in the effective instruction
- **AND** it is not added to canonical conversation history.

#### Scenario: Invalidate stale native instructions

- **GIVEN** the runtime retains a native conversation
- **WHEN** the effective instruction or advertised tool set for the next turn
  or shared memory differs from the retained conversation
- **THEN** the retained native conversation is not reused
- **AND** a fresh compatible context is initialized from bounded canonical
  history
- **AND** persisted conversation messages remain unchanged.

### Requirement: Calculator capability, disclosure, and privacy

The app SHALL persist calculator enablement locally, keep it enabled by default
until the user explicitly disables it, explain its supported numerical scope
and local execution, and advertise it only to individually validated models.

#### Scenario: Selected model cannot use calculator

- **GIVEN** calculator is enabled
- **AND** the selected model lacks verified structured calculator capability
- **WHEN** Assistant configuration or a conversation is active
- **THEN** the app reports that calculator is unavailable for the current model
- **AND** does not register a hidden tool, inject a textual tool protocol, or parse ordinary assistant text as a tool call
- **AND** normal local generation remains available.

#### Scenario: Review calculator privacy

- **WHEN** the user reviews calculator in Assistant configuration
- **THEN** the app explains that expressions and results are processed on-device
- **AND** no provider credential or network service is required
- **AND** calculation protocol and intermediate values are not persisted as visible conversation messages.

#### Scenario: Preserve an explicit calculator opt-out

- **GIVEN** the user explicitly disabled the calculator in a prior app version
- **WHEN** the updated preferences are loaded
- **THEN** the calculator remains disabled
- **AND** the user can enable it from the Tools tab.

## ADDED Requirements

### Requirement: Explicit Shared Conversation Memory

The application SHALL persist an ordered, locally editable memory shared by
normal Chat and Voice Chat across sessions. It SHALL allow direct user edits and
offer a model-only memory-write tool, enabled by default for eligible models,
with instructions to call it only in response to an explicit request to
remember. It SHALL cap editable context shared with mode-specific prompts and
remove oldest whole memory entries as needed, without rotating prompts.

#### Scenario: Save a memory with available space

- **GIVEN** an explicit request to remember a bounded fact or preference
- **WHEN** an eligible model calls the memory-write tool or the user adds it in
  the Context screen
- **THEN** the app stores a normalized entry locally
- **AND** later turns of either mode receive it in context
- **AND** the app reports what was saved.

#### Scenario: Rotate oldest entries at the shared limit

- **GIVEN** ordered memory entries and mode-specific prompts
- **WHEN** a new or edited entry would exceed the shared editable-context limit
- **THEN** the app removes oldest complete entries until the result fits
- **AND** reports which entries were removed
- **AND** rejects an entry that cannot fit even when memory is otherwise empty.

#### Scenario: Edit prompts without rotating them

- **WHEN** the user changes a mode-specific prompt in the Context screen
- **THEN** that prompt retains a simple length limit and is never automatically
  rotated
- **AND** any resulting memory eviction is disclosed before the edit is applied
- **AND** the other mode's prompt remains unchanged.

#### Scenario: Inspect and manage memory

- **WHEN** the user opens the Context screen
- **THEN** they can view memory entries and combined usage with the prompts
- **AND** they can add, edit, or remove individual entries
- **AND** they can clear all entries without deleting prompts or conversations.

#### Scenario: Keep memory local and model-only

- **WHEN** the app offers the memory-write tool
- **THEN** it is available only to a selected model with verified tool capability
- **AND** the persisted memory-write switch must be enabled
- **AND** the widget consumer cannot invoke it
- **AND** no memory or prompt content is sent to a remote memory service.

#### Scenario: Disable model memory writes

- **GIVEN** the user disables the memory-write switch on the Tools tab
- **WHEN** an eligible model starts a later Chat or Voice Chat turn
- **THEN** `memory_write` is neither advertised nor executable
- **AND** previously saved memory remains in context
- **AND** the user can still view, edit, and delete memory on the Context tab.

#### Scenario: Explain where memories are managed

- **WHEN** the user reviews the memory-write switch on the Tools tab
- **THEN** it starts enabled unless the user previously disabled it
- **AND** the UI directs the user to the Context tab to view and edit entries.
