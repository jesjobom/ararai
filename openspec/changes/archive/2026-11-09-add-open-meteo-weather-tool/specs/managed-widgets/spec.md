## ADDED Requirements

### Requirement: Weather widget source disclosure

A weather widget SHALL use only a confirmed `weather_forecast@1` capability and
SHALL show a fixed application-owned Open-Meteo attribution alongside weather
data. Tool activation and widget revision confirmation SHALL disclose remote
weather requests and require an explicitly named target place. Neither
authorship nor scheduled execution SHALL access device location.

#### Scenario: Show weather attribution

- **WHEN** a confirmed weather widget displays a valid forecast result
- **THEN** its presentation identifies Open-Meteo as the weather-data source.

#### Scenario: Device moves before scheduled refresh

- **GIVEN** a confirmed weather widget names a target place
- **WHEN** the device moves before a later scheduled execution
- **THEN** that execution continues to query the named place without requesting device location
- **AND** it does not infer a new target place from device movement.

#### Scenario: Reject location-less widget authoring

- **WHEN** a new weather widget proposal omits the target place
- **THEN** the proposal is rejected before confirmation
- **AND** a legacy location-less revision cannot access device location at execution time.

#### Scenario: Reject weather presentation that ignores forecast values

- **WHEN** a new widget declares `weather_forecast@1` but renders the same presentation for two successful results with different weather measurements
- **THEN** authoring rejects the render instead of confirming a date-only or otherwise data-independent weather card
- **AND** an actual provider failure remains a separate validation case.

#### Scenario: Route numeric weather results through a capable renderer

- **WHEN** an algorithm includes `weather_forecast@1`
- **THEN** authoring requires a custom render capable of displaying numeric measurements
- **AND** does not select collection-card or collection-list rendering, which can display only string item fields.

#### Scenario: Use a fixed presentation for a weather-only widget

- **WHEN** an algorithm calls only `weather_forecast@1` and reaches the presentation stage
- **THEN** the application supplies a bounded weather card using the result period, location, temperature and precipitation fields
- **AND** a current-weather card includes humidity when the result provides it
- **AND** the model does not need to author JavaScript or invent result links
- **AND** provider failure is shown distinctly from a forecast.

## MODIFIED Requirements

### Requirement: Application-owned constrained rendering and actions

The application SHALL render only validated presentation models through fixed
Compose components. Model-authored links and other actions SHALL be allowlisted,
user-initiated, and derived from validated structured tool results; fixed
application-owned attribution links MAY be added alongside a validated result.
Widget source MUST NOT construct Compose code, arbitrary Android intents,
WebViews, HTML, or executable callbacks.

#### Scenario: Render a Wikipedia card

- **WHEN** the active presentation contains supported text, semantic icon/style
  tokens, and a validated canonical Wikipedia link
- **THEN** the application renders them with fixed accessible components
- **AND** opens the HTTPS article only after a user action.

#### Scenario: Reject an unsafe action

- **WHEN** a presentation requests an arbitrary intent, unsupported URI scheme,
  callback, or link not traceable to an allowed result field
- **THEN** the action is omitted or the presentation is rejected
- **AND** nothing external is launched.

#### Scenario: Display an application-owned attribution link

- **WHEN** a validated weather presentation is displayed in-app or on the launcher
- **THEN** the application adds a fixed HTTPS Open-Meteo attribution link
- **AND** opening that link still requires a user action.
