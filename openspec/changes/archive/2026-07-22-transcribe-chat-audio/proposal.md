# Change: Transcribe persisted Chat audio

## Status

Superseded on 2026-07-22 by `adopt-whisper-cpp-transcription`.

The reusable conversation-domain, persistence, routing, presentation and
diagnostic foundations remain valid. The Android `SpeechRecognizer` engine is
abandoned as a product path after physical testing on Samsung SM-S942W showed
device-specific behavior: real-time delivery was complete but slow, while
fast external-audio delivery processed a 9.36-second recording at 32.39x,
returned only an initial fragment and still reported a structurally successful
segmented session. This change is archived without promoting its delta spec to
the main specification; the replacement change carries forward only the
engine-independent requirements.

## Why

Recorded Chat audio is currently useful only to audio-capable models and is
persisted without a textual representation. That prevents audio turns from
forming reconstructible session context and blocks reuse by the future
session-backed Voice Chat flow.

## What Changes

- Persist a transcript and durable transcription status with each new audio
  message while retaining the original app-owned WAV.
- Introduce a replaceable local `AudioTranscriber` boundary with an Android
  on-device implementation for API 33+ devices that expose an on-device speech
  recognizer.
- Send audio directly and transcribe asynchronously when the selected LLM
  accepts audio.
- Transcribe before generation and send text when the selected LLM does not
  accept audio.
- Offer voice input for text-only models only while the local transcriber is
  available.
- Add a persisted Chat preference, enabled by default, that controls whether
  transcripts are displayed without changing persistence or prompt context.
- Keep legacy audio messages readable without retroactive transcription.

## Impact

- Affected specs: `local-llm-hub`
- Affected areas: Chat domain state, SQLite codec, prompt construction,
  ViewModel routing, Android speech recognition adapter, Chat settings and
  message presentation.
- No hosted recognition service is permitted. Device/API/language-pack
  availability remains runtime-dependent and requires physical validation.
