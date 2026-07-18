# Design: Application settings and theme selection

## Context

`ArarAiTheme` already owns static and dynamic light/dark color schemes and uses
the system appearance by default. `MainActivity` installs that theme above the
entire Compose application. The new preference therefore belongs at the
application root rather than inside an individual screen.

## Decisions

Represent the preference as a stable `ThemeMode` enum with `System`, `Light`,
and `Dark` values. Resolve `System` against `isSystemInDarkTheme()` only at the
Compose theme boundary. This keeps stored intent separate from the currently
effective palette.

Expose the preference through a small `ThemePreferenceStore`. The Android
implementation uses the application's existing `ararai_preferences`
SharedPreferences file and publishes changes as observable state. Unknown or
missing stored values safely fall back to `System` for forward compatibility.

Create a dedicated Settings destination in the existing top-level navigation.
The first section is Appearance and its Theme choice uses mutually exclusive
radio controls. Selecting a value saves and publishes it synchronously so the
whole activity recomposes under the requested palette without restart.

Keep Material dynamic colors enabled. Theme mode determines whether the light
or dark dynamic scheme is used; on earlier Android versions it selects the
checked-in light or dark scheme.

## Validation

- Unit tests cover stored-value decoding, fallback, updates, and effective
  light/dark resolution.
- The project quality gate covers compilation, tests, lint, builds, and strict
  OpenSpec validation.
- Physical-device validation should confirm immediate visual switching,
  process-restart restoration, system-mode response, and contrast on supported
  Android versions.
