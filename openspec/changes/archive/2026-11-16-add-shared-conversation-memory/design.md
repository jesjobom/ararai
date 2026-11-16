# Design

Use the existing local instruction-preferences boundary for ordered memory entries and the shared editable-context budget. A memory item has a stable identifier and text; insertion order determines FIFO eviction. Saving or editing normalizes the text and rejects an item that cannot fit even after all older items are removed. Editing a prompt never truncates it; when its new length reduces available memory space, whole oldest entries are removed. A user-facing confirmation precedes prompt edits that evict memory.

Only one mode-specific prompt is included in a turn. The persistent budget therefore uses `max(chatPrompt.length, voicePrompt.length) + sum(memory text lengths)`, so memory always fits with either mode. This budget is a small part of the model context window, not the entire KV-cache capacity. Existing maximum prompt length remains the upper bound for the combined editable content.

The effective instruction is composed from non-editable app invariants, the active mode's prompt, and a labeled shared-memory block. It is rebuilt on each turn. Tool advertisement and dispatch follow the existing application tool registry and verified model capability; the tool writes locally and returns a bounded result including any evicted items. UI edits use the same store and rules. Memory does not enter canonical conversation history.

The memory-write Tools switch defaults on. Disabling it changes both tool advertisement and registry dispatch without deleting existing entries or removing them from later context; entries remain managed on Context. The calculator preference also defaults on, but a previously persisted `false` remains an explicit opt-out. Both switches remain subject to verified model capability.

The memory-write tool description and turn instruction ask the model to write only after a user request to remember. The model decides whether that request was made and calls the tool accordingly. The app gates the tool by the user's switch and verified model capability, but does not independently classify the conversation's intent. This is the intended simple interaction; it does not guarantee the model will never make a mistaken write.
