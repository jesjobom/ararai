---
title: Google Play Data safety draft
permalink: /data-safety/
---

# Google Play Data safety draft — official ArarAI distribution

This is a maintainer worksheet for the official ArarAI app published by
**Jesjobom**. It is not a substitute for the questions shown in Google Play
Console, legal advice, or a final declaration. Console wording and Google/Firebase
SDK data practices can change; the publisher must compare this draft with the
current form, the current Firebase disclosures, the production build, and the
[ArarAI privacy policy]({{ '/privacy/' | relative_url }}) before submission.

Forks and third-party distributions must complete their own declaration for
their own code, services, signing identity, and data practices.

## Collection and sharing overview

- **Does the app collect or share required user data types?** Yes. A user can
  explicitly submit generated-content report text and related bounded metadata
  to the official Firebase service.
- **Is all transmitted user data encrypted in transit?** Yes, through Firebase
  SDK HTTPS/TLS transport.
- **Can users request deletion?** Yes. The policy directs requests to
  <contact.ararai@gmail.com>; unsent local reports can be deleted in-app.
- **Are reports sold or used for advertising?** No.
- **Is report data transferred to a service provider?** Yes, Google Firebase
  processes it on behalf of the official operator. Confirm in the current Play
  form whether this service-provider transfer is excluded from its definition
  of "shared"; do not mark advertising or sale.
- **Is collection optional?** Yes for report content: nothing is submitted until
  the user explicitly sends a report. Security processing associated with that
  submission is required to deliver and protect the report feature.
- **Is data processed ephemerally?** Stored report content is not ephemeral.
  Some connection, authentication, attestation, and diagnostic metadata may be
  transiently processed by Firebase/Google; confirm current SDK disclosures.

## Conservative data-type mapping

Use the exact categories presented by the current Console. The likely mappings
below intentionally avoid the inaccurate claim that the app collects no data.

### App activity — Other user-generated content

Data:

- reported assistant response;
- optional user-selected textual context;
- required report reason; and
- optional report comment.

Handling:

- collected only on explicit submission;
- not sold and not used for advertising;
- used for app functionality, safety/compliance, fraud prevention/security, and
  developer communications only when follow-up is required;
- retained no more than 90 days; and
- stored in the United States.

If the current Console offers a more specific category for content reports, use
that category and keep the same purpose/retention answers.

### Personal info — User IDs

Data:

- Firebase anonymous authentication UID, pseudonymous and not an ArarAI account
  name or email address.

Handling:

- required for authenticated owner binding, abuse protection, and idempotent
  delivery when a report is submitted;
- not used for advertising, personalization, or cross-service tracking; and
- retained with the report for at most 90 days, subject to earlier deletion.

### App activity — App interactions or Other actions

Data:

- report reason, report timestamp, app version, locale, selected model/catalog
  identifier, runtime, and boolean media/transcript-presence flags.

Handling:

- collected with the user-submitted report;
- used for app functionality, report review, debugging the reported generation
  context, and safety/compliance; and
- retained with the report for at most 90 days.

Select the closest current Console subtype. Do not classify raw audio, photos,
videos, or files as collected by reporting: only presence booleans are stored.

### Device or other identifiers / security metadata

Firebase Authentication, App Check with Play Integrity, Firestore, and network
infrastructure may process app-instance, device-attestation, IP, and diagnostic
metadata for authentication, fraud prevention, security, and service operation.
ArarAI does not add these values to the stored report. Before release, review the
current Firebase Android SDK Data safety guidance and declare any data type that
Google requires the publisher to disclose, even when processing is transient or
performed by a service provider.

## Data the reporting feature does not collect

Do not mark the following as collected on account of reporting alone:

- raw photos/images or thumbnails;
- audio recordings or generated TTS;
- contacts, precise location, payment information, health information, calendar,
  SMS/call data, browsing history, or advertising ID;
- model files, hidden instructions, model reasoning, credentials, file paths,
  Android ID, hardware serial, or account email; or
- unselected conversation history.

This list does not replace review of optional network tools or future SDKs in the
actual production artifact.

## Pre-submission verification

- Verify the production dependency graph and merged manifest, including every
  Firebase SDK and optional network provider shipped in the release variant.
- Confirm the in-app disclosure, store listing, and `PRIVACY.md` describe the
  same fields, purposes, United States hosting, retention, and deletion path.
- Confirm Firebase Anonymous Auth, App Check, and Firestore production settings
  match the declared behavior.
- Confirm raw media, reasoning, hidden instructions, and unselected history are
  absent from real report documents.
- Confirm the privacy-policy URL published in Play serves the current policy over
  public HTTPS; the repository file alone may not satisfy Play's URL field.
- Revisit this worksheet before every production change that adds an SDK, data
  field, backend, analytics, advertising, account system, or media upload.
