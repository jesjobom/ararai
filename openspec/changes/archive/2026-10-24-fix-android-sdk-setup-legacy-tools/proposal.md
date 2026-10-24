# Fix CI Android SDK bootstrap

## Why

The pinned Android SDK setup action defaults to requesting the retired `tools`
package. Current `sdkmanager` rejects that package before the quality gate starts,
so pushes to `main` and Dependabot pull requests all fail at the same setup step.

## What changes

- Override the action's default package list with the supported `platform-tools`
  package.
- Keep the reviewed action SHA and the later explicit installation of the pinned
  Android SDK, Build Tools, NDK, and CMake versions unchanged.

## Validation

- Run the repository quality gate locally.
- Confirm the SDK setup step and then the quality gate in GitHub Actions after
  the workflow change is published.
