## 1. Characterize path ownership

- [x] 1.1 Add failing parser tests for traversal, absolute, empty, separator, and non-normalized segments, plus accepted nested model paths.
- [x] 1.2 Add failing filesystem-boundary tests proving escaped paths cannot be resolved, downloaded, migrated, or deleted.

## 2. Enforce containment

- [x] 2.1 Introduce one reusable model-relative-path validation rule without changing valid catalog behavior.
- [x] 2.2 Apply canonical containment at each filesystem ownership boundary before side effects occur.
- [x] 2.3 Preserve checksum verification, atomic promotion, fallback URLs, and legacy migration behavior for valid files.

## 3. Validate

- [x] 3.1 Run focused parser, resolver, downloader, migration, and deletion tests.
- [x] 3.2 Run the complete quality gate and strict OpenSpec validation.
