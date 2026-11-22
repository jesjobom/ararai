## ADDED Requirements

### Requirement: HTTPS-only model download redirects

Model downloads SHALL begin from a configured catalog source and SHALL use
HTTPS for the initial request and every subsequent redirect. A single source
attempt SHALL follow no more than ten redirects.

#### Scenario: Resume through an HTTPS redirect

- **WHEN** an HTTPS catalog source redirects a resumed download to another HTTPS URL
- **THEN** the redirected request retains the requested byte range
- **AND** the intermediate connection is released before opening the next one.

#### Scenario: Reject an unsafe redirect

- **WHEN** the redirect is missing a valid HTTPS destination or exceeds the redirect limit
- **THEN** the current source attempt fails without opening the rejected destination
- **AND** configured fallback sources remain eligible under the same policy.
