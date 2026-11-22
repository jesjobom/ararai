# Address the ArarAI static-analysis follow-ups

## Why

The initial static scan found no confirmed vulnerabilities. Three follow-ups
are worth addressing: regression coverage for the exported launcher
configuration activity, an explicit HTTPS policy for model redirects, and a
cooldown for routine Dependabot updates to GitHub Actions.

## What changes

- Exercise the launcher setup activity's rejection, cancellation, and selection
  paths without removing the export required by Android launchers.
- Follow only bounded HTTPS redirects from catalog sources, preserving download
  resume and fallback behavior.
- Apply a seven-day cooldown to GitHub Actions version updates. Keep the weekly
  schedule and do not delay security updates.
