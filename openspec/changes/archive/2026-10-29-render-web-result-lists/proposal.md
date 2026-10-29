# Render web result lists without dropping results

## Why

A widget request for up to ten ranked web results can be authored as a one-item collection card or a call with `limit=1`, silently discarding requested results.

## What Changes

- Add an application-owned `collection_list` render capability for up to ten unmodified results from one tool array, preserving order and per-result HTTPS provenance.
- For explicit numeric top-results requests, require list rendering and the requested search limit during authoring validation.
- Derive the fixed ranked-web-result presentation locally, so the final authoring stage does not depend on an extra model tool call.
- Validate list rendering against a multi-item synthetic tool result, plus empty and failed outcomes.
- Present the result list inside one card and retain its titles in the compact launcher projection.

## Impact

Widget authoring and runtime presentation helpers. Existing confirmed widgets and single-result card behavior remain unchanged.
