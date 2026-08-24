# Diagnostic error reporting

ArarAI offers diagnostic reporting only for unexpected recoverable errors. It
does not attempt to show UI or perform network work after a fatal uncaught process
exception.

## Data flow

1. An application-owned boundary restores usable Chat state and offers one
   in-memory diagnostic envelope.
2. The user reviews the disclosure and selects **Send report** or **Not now**.
3. One direct Firestore REST commit is made with 12-second connect and read
   timeouts, an anonymous Firebase Auth ID token, and an App Check token. The
   connection is closed after completion or failure. The application does not
   use the Firestore Android write cache, SQLite, WorkManager, or any retry path.
4. Firestore Security Rules validate the exact schema and permit only one
   owner-bound create in `diagnostic_error_reports`. The REST commit uses a
   create precondition and a server creation-time transform.
5. Firestore Security Rules deny reads, lists, updates, deletes, and overwrites.

The client envelope excludes prompts, responses, history, reasoning text, raw
tool calls/results, media, transcripts, paths, credentials, account information,
and stable device identifiers by construction. The transport attaches the
pseudonymous authenticated owner; Firestore supplies the server creation time,
and Rules validate the 90-day expiry.

## Configuration

No Cloud Function, Cloud Run service, or Blaze plan is required. Deploy only the
Firestore Rules from the repository root:

```sh
firebase deploy --only firestore:rules
```

Accepted data is stored in the existing default `nam5` Firestore database.
Cloud Firestore App Check enforcement must remain enabled in Firebase Console.
Production validation therefore requires a Google Play-installed build whose
App Check registration and licensing policy are already valid.

## Validation

Automated validation covers the Android envelope/coordinator, REST commit
serialization, Firestore schema/access Rules, and the common Android quality
gate. Before release, validate on a physical Play-installed build that:

- an eligible runtime failure opens the localized dialog;
- **Not now** makes no request;
- **Send report** creates one server document and shows success;
- an offline attempt shows failure and does not appear after reconnection;
- submitted fields contain no conversation or raw tool-call content.
