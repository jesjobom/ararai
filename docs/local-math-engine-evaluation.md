# Local math engine evaluation

Status: accepted for `add-local-math-tool`
Evaluated: 2026-08-04

## Decision

Use `com.ezylang:EvalEx:3.7.0` behind an app-owned adapter. Do not include
`EvalEx-big-math` in the initial implementation. Construct a strict custom
configuration with allowlisted numeric operators, functions, and constants;
never expose EvalEx's default string, date/time, boolean, array, structure,
random, data-access, or extension surface to model input.

This selection is intentionally narrower than the library. The product grammar,
limits, result schema, and error categories remain owned by ArarAI, so replacing
the evaluator later does not change the model-facing contract.

## Required initial evaluator corpus

The implementation corpus must cover:

- precedence and associativity: unary signs, parentheses, `+`, `-`, `*`, `/`,
  `%`, and bounded `^`;
- decimals and documented scientific notation, independent of device locale;
- allowlisted `abs`, `sqrt`, `min`, `max`, `ln`, `log10`, `sin`, `cos`, and
  `tan`, with radians as the trigonometric input unit;
- allowlisted `pi` and `e` constants;
- DECIMAL128 arithmetic/rounding behavior and approximate transcendental or
  fractional-power results;
- division/modulo by zero, negative square root, invalid logarithm and tangent
  domain behavior, overflow/non-finite conversion, and excessive exponents;
- empty, malformed, unknown-token, unknown-function, string, boolean, array,
  structure, implicit-multiplication, variable, and injection-like inputs;
- maximum input length, token/node count, nesting/recursion depth, function
  arity, numeric literal length, exponent magnitude, execution budget, and
  cancellation/late-result discard;
- repeatability, concurrent evaluations, cold/warm latency, and locale changes.

Expected vectors should come from independently computed constants or JDK
`BigDecimal` operations, not from EvalEx evaluating both expected and actual
values.

## Candidate comparison

### EvalEx 3.7.0 — selected

- Apache-2.0, active release on 2026-07-10, security policy, Java 11 minimum.
- 172,027-byte Maven JAR, with no runtime dependencies in the core artifact.
- Uses `BigDecimal`; precision and rounding are configurable. The default
  configuration exposes more types and functions than this product should, but
  operator/function dictionaries, constants, arrays, structures, implicit
  multiplication, and maximum recursion depth are configurable.
- Parser uses tokenization plus shunting-yard AST conversion. Evaluation is
  in-process Java; expressions do not intrinsically execute scripts, reflection,
  files, network calls, or process commands.
- Core `sqrt` is BigDecimal-based. Core trigonometric/logarithmic functions and
  fractional powers pass through `double`, so those results must be labeled and
  tested as approximate. Basic decimal arithmetic can use `MathContext.DECIMAL128`.
- Android debug spike compiled successfully with min SDK 28. The dependency
  increased the existing debug APK by 131,305 bytes (115,631,738 to
  115,763,043 bytes). This is build-specific evidence, not a release/R8 size
  guarantee.

Residual controls: use a custom empty/allowlisted dictionary rather than the
default configuration; reject variables and nonnumeric types before evaluation;
bound syntax and operands before calling the library; run on a dedicated bounded
dispatcher; apply a deadline and discard late results. Java thread cancellation
cannot be assumed to interrupt every BigDecimal operation, so prevalidation is
the primary denial-of-service control.

### EvalEx-big-math 1.0.1 — viable later extension, deferred

- Apache-2.0 wrapper over MIT `big-math` 2.3.2; adds arbitrary-precision
  transcendental functions.
- Adds two artifacts and is tied to EvalEx 3.4.0 as a provided dependency in its
  published POM, while core is now 3.7.0. That version gap needs compatibility
  proof before adoption.
- Android debug spike compiled successfully and increased the baseline APK by
  229,609 bytes total, 98,304 bytes beyond EvalEx core.

It could improve transcendental precision, but the initial calculator does not
need that additional compatibility and supply-chain surface. Reconsider it only
after real prompts demonstrate a precision requirement that core EvalEx cannot
meet.

### exp4j 0.4.8 — rejected

- Apache-2.0, no runtime dependencies, approximately 46.9 KB.
- The maintainer states that it is not in active development; the Maven artifact
  dates to 2017.
- Double-based evaluation is small and Android-friendly, but its maintenance and
  precision posture are weaker than EvalEx for a reliability-focused tool.

### parsii 4.0 — rejected

- MIT and approximately 49 KB, with a compact double-based parser.
- The latest Maven artifact found dates to 2018 and repository activity/releases
  are stale.
- It offers no material advantage over exp4j that offsets the same maintenance
  and precision concerns.

### mXparser 6.1.1 — rejected

- Feature-rich and maintained, approximately 593 KB with no required runtime
  dependencies in its published POM.
- Distributed under a dual license whose official terms include a commercial
  license. That creates avoidable product/distribution ambiguity compared with
  Apache-2.0 alternatives.
- Its much broader grammar also increases the allowlisting and adversarial-test
  surface for a narrowly scoped model tool.

### Symja / matheclipse-core 3.2.0 — rejected for this change

- Full symbolic computer-algebra system; the core JAR alone is approximately
  8.5 MB and its ecosystem has a large dependency and license surface.
- Symja documents GPL/LGPL module boundaries, including LGPL for parser/external/
  core and GPL for other modules. Correct redistribution would require a more
  detailed legal/module audit.
- Symbolic algebra and plotting are explicitly outside this change. The size,
  capability, attack surface, and licensing complexity are disproportionate to
  bounded numerical evaluation.

### App-owned parser — rejected for the initial implementation

- Can provide the smallest possible grammar and strongest direct control, with
  no third-party runtime dependency.
- Correct tokenization, precedence, numeric semantics, error positions,
  complexity limits, and adversarial hardening would become long-term
  application responsibilities.
- EvalEx already supplies these foundations behind configurable dictionaries.
  An app-owned adapter and prevalidator preserve control without maintaining a
  new parser. A minimal parser remains the fallback if Android integration or
  security tests uncover an unresolvable EvalEx issue.

## Numerical and grammar policy resulting from the research

- Core arithmetic uses `MathContext.DECIMAL128` (34 significant decimal digits,
  HALF_EVEN). Output is canonical plain/scientific decimal text using `.` and no
  device-locale formatting.
- Exactness is not overclaimed. The structured result distinguishes exact decimal
  operations from rounded division and approximate double-backed transcendental
  or fractional-power operations.
- Initial operators are unary `+`/`-`, binary `+`, `-`, `*`, `/`, `%`, and `^`.
  Initial functions are the allowlist in the evaluator corpus above. Trigonometry
  uses radians. Initial constants are `pi` and `e`.
- Implicit multiplication, variables, assignment, custom definitions, factorial,
  random, strings, booleans, comparison/logical operators, dates, durations,
  arrays, structures, regex, and arbitrary library functions are rejected.
- App-owned limits are 512 input characters, 128 lexical tokens, 16 nesting
  levels, 80 characters per numeric literal, absolute integer exponents up to
  1,000, three calls per user turn, and a 2-second evaluation deadline. EvalEx
  implicit multiplication, arrays, structures, binary values, quoted strings,
  lenient parsing, and every non-allowlisted function/operator are disabled.

## Sources and reproducible evidence

- EvalEx repository, release/tag, license, features, Java requirement, and source:
  <https://github.com/ezylang/EvalEx>
- EvalEx documentation and configuration model:
  <https://ezylang.github.io/EvalEx/>
- EvalEx Maven metadata/artifacts:
  <https://repo1.maven.org/maven2/com/ezylang/EvalEx/>
- EvalEx big-math extension and licensing:
  <https://github.com/ezylang/EvalEx-big-math>
- big-math Maven artifact:
  <https://repo1.maven.org/maven2/ch/obermuhlner/big-math/2.3.2/>
- exp4j maintenance statement and license:
  <https://github.com/fasseg/exp4j>
- parsii source and license:
  <https://github.com/scireum/parsii>
- mXparser official licensing terms and artifact:
  <https://mathparser.org/mxparser-license/>
  <https://repo1.maven.org/maven2/org/mariuszgromada/math/MathParser.org-mXparser/>
- Symja scope and module licensing:
  <https://github.com/axkr/symja_android_library>
  <https://repo1.maven.org/maven2/org/matheclipse/matheclipse-core/>

Artifact sizes were read from Maven Central `Content-Length`. APK measurements
were produced by temporarily adding the artifacts to the current ArarAI debug
build, writing temporary dependency locks, running `assembleDebug`, recording
the APK byte count, and then removing all spike-only dependency/lock changes.
