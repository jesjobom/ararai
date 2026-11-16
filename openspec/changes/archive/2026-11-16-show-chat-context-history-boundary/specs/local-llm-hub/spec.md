## MODIFIED Requirements

### Requirement: Simple Context Window Management

The shared conversation coordinator SHALL project a contiguous suffix of recent
eligible persisted user turns using a context budget based on the selected
model's configured context size, independently of whether the new turn
originates in normal Chat or Voice Chat. It SHALL keep the system instruction,
current user turn, and configured output reserve outside the droppable history.
A historical user turn and its associated assistant response, when present,
SHALL be included or omitted together. Exclusion from the projected context
SHALL NOT delete or modify canonical conversation messages.
The approximate input budget SHALL reserve an additional 10% of the configured
context capacity beyond the configured output reserve. This headroom reduces
estimation error but does not guarantee that native tokenization, tool schemas,
or generation will fit.

#### Scenario: Long session prompt construction

- **GIVEN** persisted history is longer than the selected model's context budget
- **WHEN** a new turn requires a reconstructed prompt or native-session rehydration
- **THEN** the coordinator includes the newest contiguous eligible user turns that fit the budget
- **AND** omits the first non-fitting turn and all older turns rather than backfilling shorter old messages
- **AND** does not split an associated user/assistant pair or perform automatic summarization.

#### Scenario: Compatible incremental continuation

- **GIVEN** the runtime retains compatible native conversation state for the same canonical history
- **WHEN** either Chat screen submits the next turn
- **THEN** context budgeting verifies compatibility with the projected contiguous suffix
- **AND** the runtime receives only the new user content
- **AND** unchanged persisted history is not sent again.

#### Scenario: Recent historical turn does not fit

- **GIVEN** the newest eligible historical user turn does not fit after the system instruction, current user turn, and output reserve
- **WHEN** normal Chat or Voice Chat projects the next request
- **THEN** no earlier history is substituted for that non-fitting turn
- **AND** the stored session remains complete and available to the user.

#### Scenario: Increase the configured context window

- **GIVEN** older eligible turns were omitted from a smaller projected context
- **WHEN** the user increases the selected model's effective context capacity
- **THEN** projection can include additional consecutive older turns that now fit
- **AND** it reads beyond a fixed recent-message cap when necessary using bounded persistence work
- **AND** no omitted messages need to be restored or rewritten.

#### Scenario: Additional input headroom moves the boundary

- **GIVEN** a model has a configured context capacity and output reserve
- **WHEN** Chat or Voice Chat selects historical turns for the next request
- **THEN** the approximate input budget excludes the output reserve and a further 10% of context capacity, rounded up to whole tokens
- **AND** the Chat indicator derives from that same selection
- **AND** increasing context capacity can make older complete turns eligible again.

#### Scenario: Sliding boundary invalidates a retained native conversation

- **GIVEN** a LiteRT-LM conversation retains a different historical transcript than the newly projected suffix
- **WHEN** the next turn is submitted
- **THEN** the retained conversation is not reused
- **AND** a fresh conversation receives the selected bounded history exactly once.

## ADDED Requirements

### Requirement: Visible Chat Context History Boundary

Normal Chat SHALL indicate which older canonical messages are outside the
bounded textual history projected for the next response. The indication SHALL
derive from the same inclusion decision used for generation, not a separate
visual estimate, and SHALL move when that decision changes. It SHALL NOT be
stored as a conversation message or imply that historical media are replayed.

#### Scenario: Show the boundary inside loaded history

- **GIVEN** older excluded turns and the oldest included turn are both loaded in the Chat timeline
- **WHEN** the conversation is shown
- **THEN** one accessible, non-error-styled divider appears between them
- **AND** its label explains that earlier messages are outside the context for the next response
- **AND** all messages remain visible in their original order.

#### Scenario: Boundary precedes the loaded UI page

- **GIVEN** some older turns are outside context but the exact inclusion boundary is above the currently loaded message page
- **WHEN** Chat opens the conversation
- **THEN** a compact indication near the older-history control says that some older messages are outside context
- **AND** Chat does not place a divider at an inaccurate position
- **AND** loading the relevant older page reveals the exact divider.

#### Scenario: Context settings or draft change

- **GIVEN** Chat displays a context boundary for the selected session
- **WHEN** the selected model, effective context, instruction or memory, or draft user request changes
- **THEN** Chat recomputes the boundary for the next response from the authoritative projection
- **AND** increasing available context can move the boundary toward older messages or remove it
- **AND** previously excluded messages remain stored without restoration.

#### Scenario: Freeze the submitted request's boundary while generating

- **GIVEN** a user turn is in flight
- **WHEN** Chat presents its context indication during generation
- **THEN** the indication reflects the history selected for that submitted request
- **AND** after the turn ends it may recalculate for the next response.
