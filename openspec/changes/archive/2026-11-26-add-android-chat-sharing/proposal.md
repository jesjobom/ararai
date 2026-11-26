# Add Android sharing to Chat

## Why
Users should be able to bring a screenshot, link, or selected text from another Android app into ArarAI without copying it by hand.

## What Changes
- Register ArarAI as a share target for plain text and one image.
- Open Chat and reuse its existing session dialog to choose or create a conversation.
- Populate the normal composer without submitting a message. Reuse existing image import, model-capability checks, and draft behavior.
- PDF, audio, multiple images, and persistent drafts are out of scope.

## Impact
- Affected spec: local-llm-hub.
- Affected code: Android entry point and Chat session/composer integration.
