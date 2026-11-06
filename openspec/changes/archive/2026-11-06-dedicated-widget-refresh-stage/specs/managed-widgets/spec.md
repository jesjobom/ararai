## ADDED Requirements

### Requirement: Manual refresh restarts the periodic baseline

When the user manually refreshes an enabled periodic widget and the run completes, the application SHALL replace its periodic work with a new one whose first eligible run is one configured interval after that manual refresh. It SHALL not create an immediate duplicate run or claim exact wall-clock timing. Manual-only or disabled widgets SHALL not acquire periodic work from a manual refresh. Unchanged startup reconciliation SHALL continue to preserve the pending work generation.

#### Scenario: Restart a daily cycle from manual refresh

- **WHEN** a user manually refreshes an enabled widget configured for every 24 hours
- **THEN** the existing periodic work is replaced with one whose first eligible run is approximately 24 hours after the manual refresh completes
- **AND** the refresh itself does not cause an immediate second automatic run.

#### Scenario: Manual-only widget

- **WHEN** a user refreshes an enabled widget without a periodic interval
- **THEN** no periodic work is enqueued.
