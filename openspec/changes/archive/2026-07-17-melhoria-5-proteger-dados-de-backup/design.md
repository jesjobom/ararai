## Context

ArarAI stores private Chat history in `databases/ararai_chat.db`, imported and
recorded media in `files/chat_media`, downloaded models and `.part` files in
`files/models`, the selected model in `shared_prefs/ararai_preferences.xml`, and
LiteRT-LM runtime artifacts in `cache/litert_lm`. Restoring only a subset can
both expose private content and create dangling database-to-file references.

## Goals / Non-Goals

- Make cloud backup and device-to-device transfer behavior explicit.
- Keep every app-owned data category on the current device only.
- Cover legacy full-backup and modern data-extraction configuration.
- Do not delete or migrate existing local data.
- Do not implement app-managed export/import in this change.

## Decisions

### Disable all platform-managed backup and transfer

The application sets `android:allowBackup="false"`. Preferences are not a useful
backup exception: the only current preference selects a downloaded local model,
so restoring it without the model adds inconsistency rather than user value.

### Explicit deny-all extraction rules

The manifest also references `backup_rules.xml` for Android 11 and lower and
`data_extraction_rules.xml` for Android 12 and higher. Both exclude the root,
files, databases, shared preferences, external files, and device-protected
equivalents. The modern rules apply the same exclusions to cloud backup and
device transfer. These rules document intent and provide defense in depth for
platform and OEM extraction behavior.

Android cache and no-backup directories are ineligible by platform definition;
the deny-all policy additionally ensures no other location becomes eligible if
storage layout changes later.

## Risks / Trade-offs

- Users cannot recover conversations or settings through Android restore.
- Device migration requires models to be downloaded again.
- Future portability must be an explicit, versioned app feature with clear
  handling for private media, rather than relying on opaque platform backup.
