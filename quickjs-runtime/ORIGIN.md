# QuickJS source provenance

This module vendors the library-only subset of QuickJS `2026-06-04` from the
official archive:

- Origin: `https://bellard.org/quickjs/quickjs-2026-06-04.tar.xz`
- Archive SHA-256: `b376e839b322978313d929fd20663b11ba58b75df5a46c126dd19ea2fa70ad2a`
- License: MIT (see `src/main/cpp/quickjs/LICENSE`)
- Upstream source SHA-256 (`quickjs.c`):
  `a00762d2eee42316cecbc9c15efc4549b715ec500461845cd91b2a6c38190d08`

Only the embeddable engine sources are included. `quickjs-libc`, the command
line tools, examples, and host I/O helpers are intentionally excluded. The JNI
adapter passes UTF-8 JSON and strings only; it never exposes Java objects or
callbacks to JavaScript.

To update, download a pinned official release, verify its archive and license,
replace only the listed library files, update the checksums and
`CONFIG_VERSION`, then run the native instrumentation and release-candidate
security matrix before accepting the update.
