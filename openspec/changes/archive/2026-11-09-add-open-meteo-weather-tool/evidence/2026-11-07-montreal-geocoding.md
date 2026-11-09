# Montréal geocoding follow-up (2026-11-07)

## Observed on SM-S901E

- Installed build: versionCode `29900694`, versionName `202611070354`.
- An unqualified Montréal request produced the controlled `AmbiguousLocation` result at 12:17:04 device time. A follow-up with city, province, and country produced `InvalidLocation` at 12:19:49.
- Open-Meteo geocoding returns Montreal, Canada first, with population about 1.76 million. Smaller namesakes in France and the United States also appear in the top ten. The previous ambiguity rule rejected the major city merely because another exact spelling existed.
- The previous parser treated everything after the first comma as one qualifier, so a province-and-country pair could never match either metadata field.

## Change

- Parse comma-separated city/region/country components and require every nonempty qualifier to match metadata for the same candidate, case- and diacritic-insensitively.
- Accept the provider's top-ranked city without a qualifier only when its population is at least 100,000 and ten times that of every same-name alternative. Similar-sized namesakes remain ambiguous.
- Add unit cases for dominant Montréal, a multiple-component accented qualifier, mismatched/empty qualifiers, and similarly sized Springfield namesakes within one country.

## Verification

- Targeted Open-Meteo unit tests, Detekt, and Spotless passed.
- `scripts/quality-gate.sh` passed, including tests, lint, builds, APK checks, and OpenSpec validation (10 passed, 0 failed). `git diff --check` passed.
- Installed release candidate versionCode `29901218`, versionName `202611071238`, on the same SM-S901E with `adb install -r`; first-install timestamp remained unchanged, confirming app data was preserved. APK SHA-256: `a5185faebfaf80ce302246bbdca1f152a99d7b7864db3e7eb980b6b0bc979f3d`.
- In fresh test chats on the device, “Qual a temperatura em Montreal?” and “Qual a previsao em Montreal, Quebec, Canada?” each produced `ArarAI.Weather` `outcome=success` and a completed weather answer for Montreal, Canada. The accented multi-qualifier variant was covered by the unit test, not typed on-device.
- Deleted only the two test chats. The original user chat and app data remain intact.
