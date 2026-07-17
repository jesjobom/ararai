# Change: Protect Local Chat Data from Android Backup

## Why

ArarAI persists private conversations, media, downloaded models, and runtime
artifacts in app-owned storage while Android backup remains enabled without an
explicit data-extraction policy. A local-only inference product should make its
backup and restore behavior intentional rather than inheriting platform defaults.

## What Changes

- Define the product policy for cloud backup and device-to-device transfer.
- Exclude Chat databases, Chat media, downloaded models, temporary downloads,
  and runtime caches from backup and transfer.
- Configure Android backup/data-extraction rules for supported API levels.
- Verify that restore cannot create database-to-file reference drift.
- Document the local-data and backup behavior for maintainers and users.

## Impact

- Touches Android manifest/resources and privacy documentation.
- Does not delete existing local data.
- If product policy chooses to disable all backup, that decision must be stated
  explicitly in the implementation notes.
