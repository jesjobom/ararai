# SM-S901E: year appears but event description is dropped

A second physical authoring test produced a saved random On This Day widget.
Three observed executions recorded `Success` with no diagnostic. The cached
card displays the selected article title plus `item.year` and a
provenance-bound Wikipedia link, but omits `item.text`, the event description.
Thus the technical success and year requirement do not establish full content
compliance. The earlier saved widget showed the opposite defect: event text
and link were displayed, but the event year was omitted.

The model-facing render contract previously suggested a card with one
`displayText` and a link, encouraging one text field to displace another.
The selected tool catalog listed item field names and types but not their
semantics. The generic render instructions now explain that a card may
contain separate title and body strings plus a link, and that formatting a
title must not replace the requested description. The date tool's output
schema now documents `year` as the event year, `text` as the event
description, and `title` as the linked article title. The render catalog
propagates these descriptions generically only for a selected tool. No
word-list gate or special-case rendering code was added. Existing saved
widgets were not modified; a new physical generation is required to verify
model compliance with the revised instructions.
