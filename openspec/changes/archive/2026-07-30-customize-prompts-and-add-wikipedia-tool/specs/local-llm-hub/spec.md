## MODIFIED Requirements

### Requirement: Configured Chat System Prompt

The app SHALL compose an effective system instruction from app-owned invariants
and a bounded locally persisted user instruction selected independently for
normal Chat or Voice Chat.

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
- **AND** recent eligible canonical conversation history
- **AND** the new user message.

#### Scenario: Build a Voice-Chat prompt

- **GIVEN** Voice Chat has a persisted user instruction
- **WHEN** Voice Chat submits a turn that requires context initialization
- **THEN** generation receives the app-owned invariant instruction
- **AND** the Voice-Chat user instruction
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

#### Scenario: Restore an instruction default

- **GIVEN** the user customized one interaction mode's instruction
- **WHEN** the user restores that mode's default
- **THEN** only that editable instruction returns to its checked-in default
- **AND** the other interaction mode remains unchanged
- **AND** canonical conversation history remains unchanged.

#### Scenario: Invalidate stale native instructions

- **GIVEN** the runtime retains a native conversation
- **WHEN** the effective instruction or advertised tool set for the next turn
  differs from the retained conversation
- **THEN** the retained native conversation is not reused
- **AND** a fresh compatible context is initialized from bounded canonical
  history
- **AND** persisted conversation messages remain unchanged.

## ADDED Requirements

### Requirement: Instructions and Tools Management

The app SHALL provide one destination for maintaining mode-specific user
instructions and locally persisted tool enablement.

#### Scenario: Edit instructions independently

- **WHEN** the user edits and saves the normal-Chat or Voice-Chat instruction
- **THEN** the app enforces the documented size limit
- **AND** persists the accepted text locally
- **AND** applies it only to future turns from that interaction mode
- **AND** does not modify already completed messages.

#### Scenario: Review Wikipedia networking

- **GIVEN** Wikipedia is not enabled
- **WHEN** the user reviews the tool
- **THEN** the screen explains that eligible queries and result retrieval use an
  external Wikipedia/MediaWiki service
- **AND** explains that inference and conversation storage remain local
- **AND** no Wikipedia request occurs before enablement.

#### Scenario: Selected model cannot use the enabled tool

- **GIVEN** the Wikipedia preference is enabled
- **AND** the selected model lacks verified Wikipedia tool capability
- **WHEN** the tools screen or a conversation is active
- **THEN** the app reports that Wikipedia is unavailable for the current model
- **AND** does not advertise a hidden tool to that model
- **AND** normal local generation remains available.

### Requirement: Verified Gemma Wikipedia Tool Capability

The checked-in catalog SHALL advertise Wikipedia tool capability only for
individually validated Gemma 4 LiteRT-LM bundles, and the app SHALL not infer
capability from a model family or runtime alone.

#### Scenario: Register the enabled tool

- **GIVEN** Wikipedia is enabled
- **AND** the selected installed model explicitly declares verified Wikipedia
  tool capability
- **WHEN** a LiteRT-LM conversation is initialized
- **THEN** the app registers the structured `wikipedia_search` tool
- **AND** enables LiteRT-LM automatic tool calling
- **AND** does not require a per-turn action or command phrase.

#### Scenario: Do not emulate an unsupported tool

- **GIVEN** the selected model or runtime lacks verified Wikipedia tool
  capability
- **WHEN** a conversation is initialized
- **THEN** the app does not register the tool
- **AND** does not inject a textual tool-command protocol
- **AND** does not parse ordinary assistant text as a tool call.

### Requirement: Automatic Bounded Wikipedia Retrieval

For an eligible enabled conversation, the application SHALL execute a validated
structured Wikipedia call requested by the model and return bounded untrusted
reference content for final answer synthesis.

#### Scenario: Answer without research

- **GIVEN** the Wikipedia tool is registered
- **WHEN** the model answers without requesting it
- **THEN** the app performs no Wikipedia network request
- **AND** presents the normal local response.

#### Scenario: Research automatically

- **GIVEN** the Wikipedia tool is registered
- **WHEN** the model requests `wikipedia_search` during an ordinary user turn
- **THEN** the app validates the bounded query and language
- **AND** calls a fixed official Wikipedia/MediaWiki HTTPS endpoint
- **AND** returns a bounded plain-text result to the model
- **AND** the model synthesizes the final response without another user action.

#### Scenario: Treat retrieved content as untrusted

- **GIVEN** a Wikipedia response contains text that resembles an instruction
- **WHEN** the result is supplied to the model
- **THEN** it is framed as untrusted external reference data
- **AND** cannot select an endpoint or execute another application capability
- **AND** app-owned tool and instruction rules remain authoritative.

#### Scenario: Bound repeated calls

- **GIVEN** a turn already attempted Wikipedia three times
- **WHEN** the model attempts another Wikipedia call in that turn
- **THEN** the app rejects the fourth call with a controlled result
- **AND** performs no fourth network request
- **AND** does not enter an automatic retry loop.

#### Scenario: Recover from unavailable research

- **GIVEN** a Wikipedia call is malformed, empty, oversized, unavailable, timed
  out, or cancelled
- **WHEN** the tool finishes unsuccessfully
- **THEN** the app returns a controlled error without fabricated evidence
- **AND** does not claim that research succeeded
- **AND** the conversation state machine can complete, fail, or cancel through
  its normal lifecycle.

### Requirement: Wikipedia Source Provenance

The app SHALL associate bounded validated source metadata with a completed
assistant answer that used Wikipedia without persisting raw retrieved extracts
as conversation messages.

#### Scenario: Present researched sources

- **GIVEN** Wikipedia returned one or more validated sources
- **WHEN** the final assistant answer completes successfully
- **THEN** the app persists the provider, page title, canonical HTTPS URL,
  language, and retrieval time with that answer
- **AND** normal Chat can present those sources as links
- **AND** raw extracts and intermediate tool protocol are not visible messages.

#### Scenario: Reconstruct later context

- **GIVEN** a researched assistant answer was persisted
- **WHEN** native conversation state is lost and context is reconstructed
- **THEN** the completed assistant answer remains eligible canonical history
- **AND** source metadata remains available for presentation
- **AND** the app does not repeat the historical network request
- **AND** does not require raw retrieved extracts as canonical history.

### Requirement: Latest conversation position

Normal Chat SHALL position persisted history at its newest message when the
screen opens or the selected session changes.

#### Scenario: Open or switch a conversation

- **GIVEN** the selected conversation contains more messages than fit onscreen
- **WHEN** the user opens normal Chat or selects another session
- **THEN** the list positions itself at the end
- **AND** the newest messages are visible without manual scrolling.
