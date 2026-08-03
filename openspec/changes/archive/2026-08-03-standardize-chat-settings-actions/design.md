# Design: Standardize Chat settings actions

## Decisions

### Keep persistence at the existing controller boundaries

Chat continues to use its existing setting callbacks and Voice Chat continues to
persist through `VoiceChatPreferences`. The dialogs emit changes immediately;
they do not introduce a second persistence mechanism or delayed debounce.

### Reset is non-destructive to dialog visibility

Reset emits the complete default settings state and leaves the dialog open so the
user can verify or adjust the restored values. Close never changes settings.

### Use product defaults as the single source of truth

Voice Chat resets with `VoiceChatSettings()`. Chat resets to the defaults already
declared by `ChatUiState`: reasoning disabled, reasoning output hidden, and audio
transcriptions visible.
