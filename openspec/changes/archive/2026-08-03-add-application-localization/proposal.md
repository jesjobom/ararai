# Change: Add application localization

## Why

ArarAI currently embeds English copy in its Compose UI, so users cannot choose
an application language independently from the device and new copy has no
consistent localization boundary.

## What Changes

- Add a persisted application-language preference to General Settings.
- Support the device language, English, and Brazilian Portuguese initially.
- Apply the stored locale when the activity is created; changing it may require
  restarting the application.
- Move all user-visible interface copy into Android string resources and provide
  complete English and Brazilian Portuguese translations.
- Add automated checks for preference fallback, persistence, and localized UI
  resources.

## Impact

- Affected specs: `local-llm-hub`
- Affected code: application startup, settings persistence, Compose UI, Android
  string resources, and UI tests.
