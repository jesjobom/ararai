# Open-source license disclosure

ArarAI's own source code is licensed under Apache License 2.0. The application
also uses third-party Gradle libraries, native code, and downloadable model
artifacts that remain subject to their respective licenses.

## Gradle libraries

The `com.mikepenz.aboutlibraries.plugin.android` plugin generates the resolved
direct and transitive dependency inventory during Android builds and packages
it as `R.raw.aboutlibraries`. The Settings → Open-source licenses screen renders
that inventory with the upstream attribution and license text available in
artifact metadata.

After an intentional dependency update, build the release inventory and inspect
the generated metadata for unknown or ambiguous licenses:

```sh
./gradlew :app:prepareLibraryDefinitionsRelease
find app/build -name aboutlibraries.json -print
```

An artifact with missing or incorrect published metadata requires an explicit
AboutLibraries override before release; absence of metadata is not evidence
that attribution is unnecessary.

## Native runtime

- whisper.cpp at commit `f049fff95a089aa9969deb009cdd4892b3e74916`:
  MIT License, <https://github.com/ggml-org/whisper.cpp>.

The pinned CMake revision and its upstream license must be reviewed together
whenever the native source revision changes.

## Downloadable models

- Gemma 4 E2B and E4B LiteRT-LM bundles from `litert-community`:
  repository metadata declares Apache License 2.0.
- Whisper Base and Small Q5_1 artifacts from `ggerganov/whisper.cpp`:
  repository metadata declares the MIT License.

The checked-in catalog URL, hash, repository license metadata, and model card
must be reviewed together whenever a model is added or updated. The model is
not bundled in the APK, but the app must still disclose the applicable terms
before directing users to download and use it.

## Release review boundary

Generated metadata reduces drift but does not replace legal review. Before a
Google Play release, inspect all unknown licenses, confirm required notices are
present, and verify that the release APK/AAB contains the generated inventory.
