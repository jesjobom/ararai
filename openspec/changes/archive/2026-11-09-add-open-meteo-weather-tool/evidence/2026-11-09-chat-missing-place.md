# Real-model weather without a target place — 2026-11-09 UTC

- On SM-S901E, the selected local model and enabled Chat weather tool received a fresh-chat prompt, “Qual a temperatura aqui fora”, with no city or place. In release candidate `202611081608`, it asked for a city but omitted the required explanation that ArarAI cannot access current device location.
- The weather system instruction was tightened to require both parts explicitly and to avoid calling the tool when no place is named. Targeted `InstructionPreferencesTest` and the full `scripts/quality-gate.sh` passed.
- Release candidate `202611082232` (versionCode `29903252`) was installed in place on the same device. Repeating the prompt in a new chat produced: “Para que eu possa te dizer a temperatura, por favor, me informe o nome de uma cidade ou lugar. Eu não tenho acesso à sua localização atual.” It did not invent a temperature or infer a place.
- Named-place real-model Chat responses for Montréal are recorded in `2026-11-07-montreal-geocoding.md`. A named-place saved-widget manual refresh is recorded in `2026-11-09-brasilia-state-qualifier-refresh.md`. An automatic eight-hour background refresh has not been observed.
