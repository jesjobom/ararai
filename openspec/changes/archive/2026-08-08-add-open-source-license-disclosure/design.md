# Design: Open-source license disclosure

## Decisions

### Generate Gradle metadata instead of hand-maintaining it

Use AboutLibraries' Android build plugin to collect resolved direct and
transitive dependencies and package its generated metadata. This avoids a list
that silently diverges from the release dependency graph. The Compose Material
3 renderer provides search, component details, attribution, and full detected
license text without launching an external activity.

### Keep non-Gradle artifacts explicit

whisper.cpp is fetched by CMake and the models are downloaded from the checked-in
catalog, so neither belongs to the Gradle inventory. Present these components in
a reviewed application-owned section with source URLs, license identifiers, and
license URLs. This section must cover every catalog family and native source
revision used by the app.

### Treat unknown metadata as a build/review concern

The generated inventory is evidence, not proof that upstream metadata is
correct. Dependency and model updates must review generated unknown or
ambiguous licenses. The app must not imply that the ArarAI Apache license
relicenses third-party components.

## Alternatives considered

- A static Markdown list was rejected because it omits transitive dependencies
  and drifts easily.
- Google Play OSS Licenses was not selected because it adds a Play Services UI
  boundary and still needs a separate mechanism for native/model artifacts.
