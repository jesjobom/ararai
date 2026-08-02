## ADDED Requirements

### Requirement: Experimental Web Search in Voice Chat

Voice Chat SHALL use the same enabled, credentialed `web_search` provider chain
and bounded evidence lifecycle as normal Chat while preserving the
uninterrupted half-duplex loop. It SHALL NOT speak credentials, tool protocol,
raw evidence envelopes, or source URLs.

#### Scenario: Search during a voice turn

- **GIVEN** an approved web provider is enabled and compatible
- **WHEN** the model requests web search during Voice Chat
- **THEN** Voice Chat may present or announce a short research state
- **AND** keeps capture inactive while the tool and generation own the turn
- **AND** speaks only the usable final assistant answer.

#### Scenario: Web provider fails during a voice turn

- **WHEN** the preferred provider returns a controlled authentication, quota,
  rate-limit, timeout, network, malformed-response, or cancellation failure
- **THEN** Voice Chat calls the enabled fallback for fallback-eligible failures
- **AND** reports a short safe failure or the model's usable response if the
  provider chain cannot return evidence
- **AND** returns to the listening loop without speaking protocol details.

#### Scenario: Web-assisted voice turn has no final answer

- **GIVEN** web search returned or failed
- **WHEN** generation ends without a speakable final answer
- **THEN** Voice Chat SHALL NOT start empty TTS
- **AND** persists the shared explicit incomplete response
- **AND** reports the incomplete outcome briefly before resuming the loop.

#### Scenario: Provider changes between voice turns

- **GIVEN** a retained Voice Chat conversation is bound to one web provider
- **WHEN** the user disables it, removes its token, or enables Exa while Tavily
  was the only enabled provider
- **THEN** the retained native conversation is invalidated before the next
  voice turn
- **AND** no request uses the previous provider or credential.
