# bugfix-feature-2: Fix compiler parsing and layout

- Status: In Progress
- Created: 2026-10-10
- Feature ID: bugfix-feature-2-fix-compiler-parsing-and-layout

## Synopsis

Motivation: three open bugs break the normal Advisor → compile path through
`itr-compiler`. Bug-1 (`bug-1-json-batch-key-order-rejected`) rejects valid
JSON batches whose object keys are not in one hard-coded order, misreporting
them as missing ARCH/LEGEND. Bug-2
(`bug-2-flat-cu-itr-instead-of-folder`) emits one `cu-<id>/cu-<id>.itr` blob
per regular CU even when the CU content carries the four required sections,
instead of the `cu-<id>/` folder with `COORDINATES.itr`,
`REQUIREMENTS.itr`, `IMPLEMENTATION_STEPS.itr`, `ACCEPTANCE.itr` that feat-1
specifies. Bug-3 (`bug-3-silent-blob-on-bare-section-labels`) compiles a batch
whose sections use bare labels (no trailing colon) with exit 0 into a silent
legacy blob — no warning that the sections were unrecognized — while the
partially-bare case fails loudly. Together they mean a freshly compiled feat-1
`batch.json` no longer reproduces its own checked-in ITR, batches
round-tripped through key-reordering JSON tooling fail with a misleading
error, and batches following the skill prose but omitting colons go green
into the wrong layout. This feature fixes parser, splitter, and enforcement
paths so the documented batch shapes compile to the documented layout.

Scope: `itr-compiler` batch parsing (`JSONFormat`, plus `YAMLFormat` if it
shares the ordering assumption), CU emission (`FileSystem.write` /
component splitting), and enforcement of the flat-CU fix. In:
key-order-independent parsing, inline-section → components splitting, writer
branch coverage via the real batch-file path, and a hard fail naming the CU
and missing component(s) whenever a regular CU would otherwise land as a
flat/single-file blob, plus bare-label handling (accept bare labels as
aliases or fail naming them — never a silent blob). Out: `dtr-builder`,
`run-waves`, skill-doc rewrites except where the batch format itself changes.

## Implementation status

In Progress. Waves 1–3 merged in commit `078c65b`
(`Fix: itr-compiler batch parsing + CU layout (bugfix-feature-2, waves 1-3
consolidated)`); ITR directory
`.LLMDD/ITRS/bugfix-feature-2-fix-compiler-parsing-and-layout/`. Intended to
close `bug-1-json-batch-key-order-rejected`,
`bug-2-flat-cu-itr-instead-of-folder`, and
`bug-3-silent-blob-on-bare-section-labels` once E2E verify is green.
Criterion 9 / test T7 (bare labels, bug-3) still needs a follow-up CU.

## Acceptance criteria

1. A JSON batch with CU object keys in any order (e.g. `content` first, or
   alphabetical via `sort_keys`) compiles with exit 0 when `arch` and
   `legend` are present with valid values.
2. Batches with byte-identical values but different key orders parse to equal
   `CUBatch` results (no order-dependent drops).
3. A regular CU whose `content` carries the four labeled sections
   (`REQUIREMENTS` / `COORDINATES` / `IMPLEMENTATION_STEPS` / `ACCEPTANCE`)
   compiles to a `cu-<id>/` folder holding `COORDINATES.itr`,
   `REQUIREMENTS.itr`, `IMPLEMENTATION_STEPS.itr`, `ACCEPTANCE.itr`, with no
   `cu-<id>/cu-<id>.itr` blob.
4. Recompiling `.LLMDD/ITRS/feat-1-per-cu-itr-directories/batch.json` into an
   empty folder reproduces the checked-in layout: four component files per
   `cu-001`…`cu-005`, root `ARCH.itr`/`LEGEND.itr`, no per-CU blobs.
5. No regular-CU `.itr` file ever lands at the ITR root; root holds globals
   (`ARCH.itr`, `LEGEND.itr`, `SYNOPSIS.itr`) only.
6. A genuinely component-less legacy CU (no sections, no `components` object)
   keeps exactly one `<cu-id>.itr` frame inside its own folder (feat-1
   criterion 11 preserved).
7. Enforcement: compilation of a new-schema batch fails with non-zero exit
   and names the offending CU id plus the missing component section(s) when
   any regular CU omits one or more of the four required sections; no flat
   `cu-<id>.itr` blob (at root or inside the CU folder) is left behind for
   the offending CU.
8. `sbt test` stays green (no regressions in existing parser/writer suites).
9. Bare labels never go green silently: a CU whose sections use bare labels
   (no trailing colon) either compiles to the four component files (bare
   labels accepted as aliases) or fails with non-zero exit naming the
   unrecognized labels; exit 0 with a legacy blob for such a CU is forbidden.

## Tests

- `T1` (criteria 1–2, bug-1 repro): parse/compile the two minimal batches
  from bug-1 (`content`-first vs `cu-id`-first, identical values) via
  `.LLMDD/tools/itr-compiler --compile --json-content <batch> --out-folder
  <tmp>/`; assert exit 0 and `Compiled 3 CU(s)` for both, plus a new
  `sbt "testOnly *JSONFormat* -- -z key-order"` unit test asserting equal
  `CUBatch` results across key orders.
- `T2` (criterion 3, bug-2 minimal repro): compile `/tmp/batch_inline.json`
  from bug-2; assert `test -f <out>/cu-001/COORDINATES.itr`,
  `REQUIREMENTS.itr`, `IMPLEMENTATION_STEPS.itr`, `ACCEPTANCE.itr` and `test
  ! -e <out>/cu-001/cu-001.itr` plus `test ! -e <out>/cu-001.itr`.
- `T3` (criterion 4): `rm -rf /tmp/itr_repro_feat1 &&
  .LLMDD/tools/itr-compiler --compile --json-content
  .LLMDD/ITRS/feat-1-per-cu-itr-directories/batch.json --out-folder
  /tmp/itr_repro_feat1`; assert per-CU four-file layout for `cu-001`…`cu-005`
  with `find /tmp/itr_repro_feat1 -name "cu-*.itr" | wc -l` equal to 0 and
  `find /tmp/itr_repro_feat1 -name "*.itr" -maxdepth 1` showing globals only.
- `T4` (criteria 5–6): compile a legacy component-less batch; assert
  `<out>/cu-old/cu-old.itr` exists, `<out>/cu-old.itr` does not, and no
  component files are emitted for it.
- `T5` (criterion 8): `sbt test` from `itr-compiler/`; expected all suites
  green.
- `T6` (criterion 7, flat-CU enforcement): compile a new-schema batch whose
  regular CU omits one required section (e.g. no `ACCEPTANCE`); assert
  non-zero exit, stderr names the CU id and the missing section, and no
  `<out>/cu-<id>.itr` flat file nor `<out>/cu-<id>/cu-<id>.itr` blob exists
  for the offending CU.
- `T7` (criterion 9, bug-3 bare labels): re-run bug-3's CLI repros —
  the all-bare batch must either yield
  `<out>/cu-001/{COORDINATES,REQUIREMENTS,IMPLEMENTATION_STEPS,ACCEPTANCE}.itr`
  or fail non-zero naming the unrecognized labels (never exit 0 with only
  `<out>/cu-001/cu-001.itr`); the all-colon batch stays green with four
  component files; plus a parser-level test over a bare-label batch
  asserting components-or-error instead of silent empty.
