---
title: Generated-content reporting operations
permalink: /reporting-operations/
---

# Generated-content reporting operations

This runbook defines the public operating policy for the official ArarAI
distribution published on Google Play by **Jesjobom**. **Jairton Junior** is the
initial operational owner and reviewer. Privacy, support, deletion, abuse, and
incident contact uses <contact.ararai@gmail.com>.

Independent distributors and forks must provision a separate Firebase project,
publish their own privacy policy and Data safety declaration, appoint their own
reviewer, and operate their own retention and incident processes. They must not
treat the official `ararai-report` project as a shared community backend.

Private account identifiers, recovery methods, debug tokens, credentials,
incident evidence, and emergency contacts do not belong in this public runbook.

## Supported reports and languages

The official operation accepts the following reasons:

- **Hate or harassment:** attacks, dehumanization, threats, or targeted abuse.
- **Sexual content:** explicit sexual material or inappropriate sexual content.
- **Violence or self-harm:** graphic violence, encouragement of self-harm, or
  credible threats to safety.
- **Dangerous or illegal content:** instructions or encouragement that create a
  material risk of harm or facilitate illegal activity.
- **Privacy concern:** disclosure, inference, or solicitation of personal or
  sensitive information.
- **False or misleading information:** materially deceptive claims that may
  cause harm or seriously mislead a user.
- **Other:** relevant safety or policy concerns not covered above.

Human review is officially supported in Portuguese and English. Reports in
another language may be assessed when the meaning can be established safely;
otherwise they are closed as unsupported or reviewed with a privacy-appropriate
translation process before a decision is made. Report text must not be copied to
consumer translation or AI services without a separately reviewed data-
processing basis.

## Review workflow

The operator maintains a minimal private review log keyed by report ID. It does
not duplicate report text. Valid states are:

1. **Received** — accepted by Firestore and awaiting review.
2. **In review** — assigned to the official reviewer.
3. **Resolved** — reviewed and any necessary product or policy follow-up noted.
4. **Dismissed** — duplicate, unintelligible, unsupported, or not actionable.
5. **Escalated** — credible urgent harm, illegality, coordinated abuse, or a
   privacy/security incident requires a separate response.
6. **Deleted** — removed after resolution, a valid request, or expiry.

Review is a best-effort safety process, not a promise of continuous monitoring,
emergency response, individual replies, or a specific outcome. ArarAI is not an
emergency service. The reviewer avoids opening unrelated user data and records
only the minimum decision and timing needed for accountability.

## Abuse response

Duplicate or spam reports may be dismissed together. The operator may restrict
an abusive pseudonymous Firebase account or strengthen App Check/rules when that
can be done without blocking legitimate access to the mandatory reporting path.
The production rules must never be opened, collection listing must never be
enabled for clients, and quota pressure must never be addressed by collecting
more identifying data without a new reviewed change.

Credible imminent physical harm, child-safety material, serious privacy
exposure, or other potentially unlawful content is escalated to the operator for
case-specific handling. The operator preserves only what is necessary, avoids
redistributing harmful content, and seeks qualified legal or emergency guidance
when appropriate.

## Incident response

For suspected unauthorized access, disclosure, corruption, or loss:

1. restrict affected administrative access and preserve relevant audit evidence;
2. identify the affected time range, report IDs, fields, and service boundary;
3. stop further exposure without weakening the local Chat or deleting evidence
   needed for a proportionate investigation;
4. rotate or revoke affected credentials and correct rules/configuration;
5. assess notification, contractual, and legal obligations for affected people
   and service providers;
6. delete data that is unsafe or no longer justified, document the decision, and
   record preventive follow-up; and
7. publish an appropriate user-facing notice when material risk warrants it,
   without exposing operational secrets or report content.

Incident records are private. Public source history may describe a remediation
after sensitive details have been removed.

## Retention and deletion

Accepted reports have a maximum retention of 90 days. They may be deleted
earlier after resolution or following a valid request. Each report stores
`expiresAt`, but managed Firestore TTL is not enabled on Spark.

Once per month, the operator:

1. queries administratively for documents whose `expiresAt` is at or before the
   current time;
2. deletes those documents using Firebase Console or a separately controlled,
   least-privilege administrative tool;
3. confirms the expired set is absent; and
4. records the execution date, operator, item count, and outcome without copying
   report content.

Deletion requests received at <contact.ararai@gmail.com> are handled under the
privacy policy. The anonymous architecture may require an approximate timestamp
and limited content match to locate the report safely. The operator does not ask
for passwords, credentials, or identity documents by default.

## Spark quota operation

The official project remains on Firebase Spark while the reporting workload
fits the no-cost Firestore quota. The owner reviews Firestore usage and rejected
requests weekly during an initial production rollout and at least monthly after
usage stabilizes. The review covers document writes/reads, stored bytes, App
Check metrics, authentication anomalies, rules denials, and pending-report user
feedback.

If quota or service availability is exhausted:

- local Chat, models, conversations, and media remain available;
- the app retains only its bounded private pending queue and retries later;
- users can inspect and delete unsent reports;
- production Security Rules and App Check are not weakened; and
- the operator records the interruption and assesses whether capacity,
  architecture, or billing needs an explicit new decision.

Managed TTL, a server endpoint, or a paid plan is not enabled silently. Any such
change requires a reviewed architecture, privacy, cost, and deployment update.

## Administrative access

Administrative access is limited to the operational owner and any explicitly
approved backup reviewer who needs it. Accounts use multi-factor authentication
and individual identities; credentials and service-account keys are never
shared, committed, embedded in the app, or stored in the public runbook.

Access is reviewed at least quarterly and immediately after a role change or
incident. Unused access is removed. Routine review should use the narrowest
Firebase/Google Cloud role that permits the required action. Broad owner/editor
roles are reserved for project administration and not used as daily review
credentials when a narrower role is practical.

Development/emulator data and production reports remain separated. App Check
debug tokens and debug-provider builds are never accepted as production
attestation.
