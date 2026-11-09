# Offer eight-hour widget refresh

## Why

The dedicated schedule stage currently offers only six intervals, even though the widget scheduler and saved-widget settings already support every whole hour from 1 to 24. A request to refresh three times per day correctly converts to eight hours but fails validation as `InvalidSchedule`.

## What Changes

Add eight hours to the model's offered intervals. Keep the bounded choices, existing schedule-stage workflow, and legacy checkpoint behavior unchanged.
