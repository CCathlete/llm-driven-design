# bug-2: Compiler emits cu-*.itr blobs instead of CU folder with component files

- Status: Open
- Created: 2026-10-10
- Resolved: -
- Bug ID: bug-2-flat-cu-itr-instead-of-folder
- Related features: feat-1-per-cu-itr-directories

## Bug detail

Symptoms: compiling a normal Advisor-drafted batch produces one
`cu-<id>/cu-<id>.itr` blob per regular CU instead of a `cu-<id>/` folder
holding the four separate component files (`COORDINATES.itr`,
`REQUIREMENTS.itr`, `IMPLEMENTATION_STEPS.itr`, `ACCEPTANCE.itr`).

Reproduction (from repo root, needs only the prebuilt binary):

```sh
rm -rf /tmp/itr_repro_feat1
.LLMDD/tools/itr-compiler --compile --json-content .LLMDD/ITRS/feat-1-per-cu-itr-directories/batch.json --out-folder /tmp/itr_repro_feat1
# EXPECTED: per feat-1 criteria 1-5/11 and the checked-in example ITR,
#   /tmp/itr_repro_feat1/cu-001/COORDINATES.itr
#   /tmp/itr_repro_feat1/cu-001/REQUIREMENTS.itr
#   /tmp/itr_repro_feat1/cu-001/IMPLEMENTATION_STEPS.itr
#   /tmp/itr_repro_feat1/cu-001/ACCEPTANCE.itr
#   and no cu-001/cu-001.itr blob anywhere.
# ACTUAL (exit 0): Compiled 7 CU(s), all regular CUs land as single blobs:
#   /tmp/itr_repro_feat1/cu-001/cu-001.itr
#   /tmp/itr_repro_feat1/cu-002/cu-002.itr
#   /tmp/itr_repro_feat1/cu-003/cu-003.itr
#   /tmp/itr_repro_feat1/cu-004/cu-004.itr
#   /tmp/itr_repro_feat1/cu-005/cu-005.itr
find /tmp/itr_repro_feat1 -type f | sort
```

Minimal repro with the same shape (no `components` key, sections inline in
`content` — exactly what the `compile-itr` skill's "CU content bar" tells the
Advisor to draft):

```sh
cat > /tmp/batch_inline.json <<'EOF'
[{"cu-id":"arch","cu-type":"arch","dtr-coordinates":[],"content":"arch rules"},{"cu-id":"legend","cu-type":"legend","dtr-coordinates":[],"content":"legend text"},{"cu-id":"cu-001","dtr-coordinates":[],"content":"REQUIREMENTS: build X. COORDINATES: TYPE.Foo. IMPLEMENTATION_STEPS: (1) edit a/b. ACCEPTANCE: test T1 green."}]
EOF
rm -rf /tmp/itr_inline && .LLMDD/tools/itr-compiler --compile --json-content /tmp/batch_inline.json --out-folder /tmp/itr_inline
# EXPECTED: /tmp/itr_inline/cu-001/{COORDINATES,REQUIREMENTS,IMPLEMENTATION_STEPS,ACCEPTANCE}.itr
# ACTUAL: /tmp/itr_inline/cu-001/cu-001.itr only
```

The checked-in example `.LLMDD/ITRS/feat-1-per-cu-itr-directories/cu-001/`
shows the intended layout (four component files, no blob), so a fresh
compile of its own `batch.json` no longer reproduces it.

Environment: `.LLMDD/tools/itr-compiler` (and identical
`itr-compiler/itr-compiler`, 6092538 bytes, built Oct 9), Linux, OpenJDK 21.

Suspected cause: `JSONFormat.parse()`
(`itr-compiler/src/main/scala/itrcompiler/infrastructure/parsers/JSONFormat.scala`)
populates `CU.components` only from an explicit `"components": {...}`
object (`parseComponentsForCU` / `parseComponentsObject`). Advisor batches
carry the four sections inline in the `content` string (the `compile-itr`
skill documents only `cu-id` / `dtr-coordinates` / `content`, no
`components` key), so `components` stays empty. `FileSystem.write()`
branches on `components.nonEmpty`: empty takes the single-blob branch
(`<cu-id>/<cu-id>.itr`). Nothing ever splits the inline
`REQUIREMENTS:` / `COORDINATES:` / `IMPLEMENTATION_STEPS:` / `ACCEPTANCE:`
sections into components. Unit/integration tests pass because
`Feat1LayoutTest` constructs `CU(..., components = fullComponents(...))`
directly, bypassing the parser — the end-to-end batch-file path is
uncovered.

## Resolution status

Open. No fix merged.

## Fix summary (Resolved only)

Pending.

## Tests proving resolution (Resolved only)

Pending. Suggested: re-run the two CLI repro commands above and assert the
four component files exist per CU with no `cu-<id>/cu-<id>.itr` blob; plus a
parser-level test feeding an inline-section batch and asserting the parsed
`CU.components` (or the written folder) contains all four components.
