## 1. Specification

- [x] 1.1 Define selected-language persistence, startup application, fallback,
  and full-interface localization behavior.

## 2. Localization foundation

- [x] 2.1 Add and test the application-language preference model and store.
- [x] 2.2 Apply the selected locale before activity creation.
- [x] 2.3 Add language selection to General Settings.
- [x] 2.4 Add English and Brazilian Portuguese resources for Home, navigation,
  and General Settings.
- [x] 2.5 Reflect a newly persisted language selection immediately and offer
  supported Activity recreation to apply it.
- [x] 2.6 Confirm Android Back exit from Home, persist the optional "do not ask
  again" choice, and close the application task without killing its process.

## 3. Complete interface migration

- [x] 3.1 Migrate Home copy to resources.
- [x] 3.2 Migrate Chat and conversation-management presentation copy to resources.
- [x] 3.3 Replace user-visible Chat errors emitted as raw strings with typed,
  localizable presentation states.
- [x] 3.4 Migrate Voice Chat and voice settings copy to resources.
- [x] 3.5 Migrate model management, download, diagnostics, and benchmark
  presentation copy, including the Whisper candidate workflow.
- [x] 3.6 Localize model capability metadata and remaining benchmark state
  values without comparing presentation strings.
- [x] 3.7 Migrate assistant instructions, generation, and tool settings copy.
- [x] 3.8 Translate accessibility descriptions, validation errors, empty states,
  dialogs, notifications, and remaining dynamic presentation mappings.
- [x] 3.9 Add a deterministic check that prevents new user-visible hard-coded
  strings and verifies resource parity between supported locales.

## 4. Validation

- [x] 4.1 Run targeted preference and Compose tests.
- [ ] 4.2 Run the complete quality gate and strict OpenSpec validation.
- [x] 4.3 Verify locale persistence and both translations on a physical device;
  record that restart is currently required after changing language.
