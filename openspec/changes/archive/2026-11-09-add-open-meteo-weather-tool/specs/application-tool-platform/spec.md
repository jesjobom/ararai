## ADDED Requirements

### Requirement: Opt-in shared weather forecast tool

The application SHALL offer a stable `weather_forecast@1` contract to eligible
  models and managed widgets. MODEL and WIDGET enablement SHALL remain independent
  and disabled by default. A request SHALL select a bounded time period and
  require an explicit place name. The application SHALL resolve place names and
retrieve only allowlisted current, hourly, and daily weather fields from
Open-Meteo. It SHALL return bounded structured data with location, local time,
units, retrieval time, source attribution, and controlled failure states.

#### Scenario: Answer a current weather question

- **GIVEN** an eligible model and enabled Chat weather tool
- **WHEN** the model requests current weather for an explicit city
- **THEN** the application resolves that city, fetches current conditions, and returns a bounded result with local time and units
- **AND** the model can formulate the answer without receiving raw provider JSON.

#### Scenario: Answer a rain or week-forecast question

- **WHEN** a weather request selects today, tomorrow, or the next seven days
- **THEN** the tool returns the relevant daily summaries and bounded hourly precipitation data when needed
- **AND** any umbrella recommendation remains an interpretation of those values, not an unsupported provider claim.

#### Scenario: Keep model weather context bounded

- **WHEN** a model receives today's or tomorrow's hourly forecast
- **THEN** it receives compact rain windows covering the local day, with peak rain chance and accumulated precipitation
- **AND** widget consumers retain the full structured hourly result.

#### Scenario: Continue a weather conversation

- **WHEN** the user asks another question after a weather tool result
- **THEN** the application submits the follow-up without blocking on reuse of the prior native conversation
- **AND** the prior chat transcript remains available to the model.

#### Scenario: Reject ambiguous or unavailable place

- **WHEN** an explicit place resolves ambiguously or the provider is unavailable
- **THEN** the tool returns a controlled failure rather than guessing a place or presenting stale weather as current.

#### Scenario: Ask for a target place

- **WHEN** the user requests weather without naming a target place
- **THEN** the model asks for a city or other target place and explains that the app cannot access current device location
- **AND** a direct or legacy tool invocation without a place returns a controlled `LocationRequired` failure without contacting Open-Meteo.

#### Scenario: Resolve a dominant city and regional qualifiers

- **WHEN** a city name has much smaller namesakes but one clearly dominant geocoding result
- **THEN** the tool selects the dominant city and identifies its country in the result.
- **WHEN** the request includes region and country qualifiers separated by commas
- **THEN** every qualifier must match the same geocoded city, ignoring case and diacritics.
- **AND** Brazilian state abbreviations and a Portuguese country name match only their corresponding provider region and country.
- **AND** similarly sized namesakes remain ambiguous until the user qualifies the place.

### Requirement: Explicit place required for weather

Every weather request SHALL name a target place. The weather tool SHALL never
access device location or request Android location permission. Its activation
UI SHALL disclose that the named place is sent to Open-Meteo.

#### Scenario: Use a named place without device permission

- **GIVEN** the user explicitly enabled weather
- **WHEN** a weather request names a target place
- **THEN** the application resolves it without requesting Android location permission.

#### Scenario: Reject a missing place

- **WHEN** a Chat model invokes the weather tool without a target place
- **THEN** the tool returns `LocationRequired` and does not query Open-Meteo
- **AND** the model is instructed to ask the user for the target place.
- **WHEN** a widget draft omits a target place
- **THEN** its tool call is rejected before confirmation.

#### Scenario: Refresh a widget with a named place

- **WHEN** a scheduled widget has a named target place
- **THEN** it can refresh without Android background-location permission
- **AND** a legacy location-less revision fails without using any stored device coordinates.
