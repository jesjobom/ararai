# SM-S901E: call-function argument shape, 2026-10-14

The same persisted authoring session that had failed at algorithm revision 2
advanced after the previous APK installation. Feasibility revision 3 and
algorithm revision 3 were accepted. The new call stage
`call:get_today_events` failed all three attempts with
`InvalidToolArguments`.

The first two generated functions returned `{toolId, args}`. The third
returned `{toolId, contractVersion, arguments}`. The call stage requires only
the selected tool's input-argument object; the application builds the call
metadata. The existing generic `InvalidToolArguments` repair instruction
incorrectly told the model to keep alias, tool ID and version, reinforcing the
wrapper error. No tool/network request was executed in these failed attempts.

The initial stage objective, selected-tool context and call-specific repair
now consistently specify a bare `inputSchema` argument object and explicitly
exclude call metadata and wrapper fields. The generic repair wording no
longer tells the model to generate metadata. Regression tests cover initial
and repair instructions. Directed tests, Spotless, Detekt, the full debug unit
suite, lint and debug build passed. OpenSpec strict validation passed. Debug
APK SHA-256 `56c83e71ca81cb0641cf586c19089091914d02811e600ce92c68c90c3f7c4da0`
was installed successfully; Android reports local update time
`2026-10-14 00:23:26` and version `202610140020`. The saved session remains
at the call stage for a physical retry; successful model generation is not
yet claimed.
