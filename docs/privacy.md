---
title: ArarAI Privacy Policy
permalink: /privacy/
---

# ArarAI Privacy Policy

Effective date: August 14, 2026

This policy applies to the official ArarAI Android application distributed on
Google Play by **Jesjobom** and operated by **Jairton Junior**. Independent
forks, modified builds, and third-party distributions are operated by their
respective distributors and must provide their own privacy information and
backend configuration.

## Local-first operation

ArarAI runs supported language models on the device. Conversations, prompts,
generated responses, images, audio recordings, transcripts, downloaded models,
preferences, and runtime caches remain in app-owned local storage during normal
use. Android backup and device transfer are disabled for the application.

Network access may still occur for features the user invokes, including model
downloads, optional knowledge tools, and generated-content reporting. Their
individual boundaries are described below and in the application before data is
submitted.

## Generated-content reports

ArarAI sends a generated-content report only after the user opens the report
flow, reviews the selected content, chooses a reason, and explicitly submits it.
Opening the flow or enabling Chat does not send conversation content.

A submitted report contains:

- the assistant response selected by the user;
- a required report reason and an optional comment;
- only the preceding textual context individually selected in the review flow;
- application version, locale, selected model identifier, runtime, report time,
  and boolean indicators that image, audio, or a transcript participated in the
  reported turn; and
- a random report identifier and a pseudonymous Firebase anonymous user
  identifier used for ownership, abuse protection, and idempotent delivery.

Reports do **not** include raw images, image thumbnails, audio recordings,
generated speech, model files, hidden instructions, model reasoning, file paths,
credentials, advertising identifiers, Android ID, hardware serial, account
email, or conversation history the user did not select. Report text may itself
contain personal information if the user chooses to submit such text.

Firebase Authentication, App Check with Play Integrity, Firestore, and their
underlying network services may transiently process security and connection
metadata, such as IP address, app/device attestation, and service diagnostics,
to authenticate requests, prevent abuse, maintain security, and operate the
service. ArarAI does not add that transient metadata to the stored report.

## Purpose and legal basis

Submitted reports are used to review potentially harmful, offensive, illegal,
misleading, privacy-invasive, or otherwise inappropriate generated content; to
meet application-store safety obligations; to investigate abuse; and to protect
the service. Submission is user-initiated and optional. The local Chat remains
usable when reporting is unavailable.

## Service providers and international transfer

The official distribution uses Google Firebase as a service provider for
anonymous authentication, application attestation, and report storage. Accepted
reports are stored in the Firebase project `ararai-report`, in the default
Cloud Firestore `nam5` United States multi-region. Submitted report content and
associated allowlisted metadata are therefore transferred to and hosted in the
United States, including when the user is elsewhere.

Data is encrypted in transit using HTTPS/TLS. Access to accepted reports is
restricted to the report-review operation; the Android client cannot list,
update, or delete the report collection and can point-read only its own
deterministic report document for delivery idempotency.

## Retention and deletion

Accepted reports are retained for no more than 90 days and may be deleted
earlier after review, when no longer needed, or following a valid deletion
request. While the official Firebase project remains on its no-cost Spark plan,
expiry is reviewed and performed manually at least once each month.

Unsent reports are stored in private application storage for no more than seven
days and are bounded in number and size. The user can delete an unsent report in
the application. Accepted report content is removed from the local queue after
successful delivery. Only the latest report identifier and completion time may
remain in app preferences, without report content, and are used solely to decide
whether to show short-lived delivery feedback.

To request access to or deletion of a submitted report, contact
<contact.ararai@gmail.com>. Because official reports use anonymous rather than
named accounts, include the approximate submission date and time and, only if
you are comfortable doing so, a short excerpt sufficient to locate the report.
Do not send passwords, credentials, identity documents, or unrelated
conversation content. A request may require additional information to locate a
report and establish that disclosure would not expose another person's data.

## Optional network features

Model downloads contact the artifact locations declared in ArarAI's checked-in
catalog. Optional Wikipedia and experimental web-search providers have separate
in-app controls and bounded disclosures. Provider credentials supplied by a user
are stored with Android Keystore-backed encryption and are not included in
model context, conversation history, reports, logs, backup, or export.

## Security and incidents

ArarAI limits stored report fields and sizes, uses anonymous authentication,
application attestation, create-only client access, and least-privilege
administrative review. No system is completely secure. Suspected privacy or
security incidents involving the official distribution can be reported to
<contact.ararai@gmail.com>.

## Changes and contact

Material changes to this policy will be versioned with the official source and,
when appropriate, reflected in the published application or store listing.

- Data controller/operator for the official distribution: **Jairton Junior**
- Google Play publisher: **Jesjobom**
- Privacy and support contact: <contact.ararai@gmail.com>
