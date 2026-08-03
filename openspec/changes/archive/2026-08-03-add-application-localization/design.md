# Design: Application localization

## Decisions

### Use Android resources as the UI copy boundary

Compose screens resolve user-visible copy through `stringResource`; dynamic
values use formatted resources. Domain and diagnostic values remain typed and
are mapped to localized copy at the presentation boundary.

### Persist a small, stable language identifier

`ApplicationLanguagePreferenceStore` stores enum names in the existing private
application preferences. Unknown values fall back to `System`, preserving a
safe upgrade path.

### Apply locale before activity creation

`MainActivity.attachBaseContext` wraps the base context with the persisted
locale. The settings composition separately tracks the newly persisted choice
so selection feedback is immediate. A restart notice can recreate the Activity,
causing the new instance to apply the stored locale before composing its UI.

### Keep Android Home and root Back semantics distinct

Android Home retains the application Activity in the background. Android Back
from the application Home destination presents a localized confirmation and,
when confirmed, uses `finishAndRemoveTask` to close the Activity task. The app
does not kill its own process. A private preference stores the user's optional
"do not ask again" choice.

## Initial languages

- System/device language, with the default English resources as fallback.
- English (`en`).
- Brazilian Portuguese (`pt-BR`).

Additional languages require only a preference option and a complete Android
resource translation.
