# bug-3: Silent blob fallback on bare section labels

- Status: Open
- Created: 2026-10-10
- Resolved: -
- Bug ID: bug-3-silent-blob-on-bare-section-labels
- Related features: -

## Bug detail

Symptoms: a batch whose CU `content` carries the four sections with
**bare** labels (`REQUIREMENTS` without a trailing colon) compiles with
exit 0 and silently emits the legacy single-blob layout
(`cu-<id>/cu-<id>.itr`) instead of the four component files — no warning
that the sections were not recognized. The `compile-itr` skill's "CU
content bar" names the sections without showing the colon requirement,
so an Advisor following the skill naturally drafts bare labels and gets
blobs with a green exit code.

Reproduction (from repo root; needs only `java`, uses the repo-root
assembly `itr-compiler/itr-compiler`, 6097863 bytes, built Oct 10):

```sh
# 1. all-bare labels: SILENT legacy blob, exit 0
cat > /tmp/batch_allbare.json <<'EOF'
[{"cu-id":"arch","cu-type":"arch","dtr-coordinates":[],"content":"arch rules"},{"cu-id":"legend","cu-type":"legend","dtr-coordinates":[],"content":"legend text"},{"cu-id":"cu-001","dtr-coordinates":[],"content":"REQUIREMENTS\nBuild X.\nCOORDINATES\nTYPE.Foo.\nIMPLEMENTATION_STEPS\n1. Edit a/b.\nACCEPTANCE\nTest T1 green."}]
EOF
rm -rf /tmp/itr_allbare && ./itr-compiler/itr-compiler --compile --json-content /tmp/batch_allbare.json --out-folder /tmp/itr_allbare
# EXPECTED: four component files (or a loud error naming the bad labels)
# ACTUAL: Compiled 3 CU(s), exit 0, only /tmp/itr_allbare/cu-001/cu-001.itr

# 2. same content with colon labels: components, exit 0
cat > /tmp/batch_allcolon.json <<'EOF'
[{"cu-id":"arch","cu-type":"arch","dtr-coordinates":[],"content":"arch rules"},{"cu-id":"legend","cu-type":"legend","dtr-coordinates":[],"content":"legend text"},{"cu-id":"cu-001","dtr-coordinates":[],"content":"REQUIREMENTS:\nBuild X.\nCOORDINATES:\nTYPE.Foo.\nIMPLEMENTATION_STEPS:\n1. Edit a/b.\nACCEPTANCE:\nTest T1 green."}]
EOF
rm -rf /tmp/itr_allcolon && ./itr-compiler/itr-compiler --compile --json-content /tmp/batch_allcolon.json --out-folder /tmp/itr_allcolon
# EXPECTED and ACTUAL: Compiled 3 CU(s), exit 0,
#   cu-001/{COORDINATES,REQUIREMENTS,IMPLEMENTATION_STEPS,ACCEPTANCE}.itr

# 3. mixed labels (two bare, two colon): LOUD failure, exit 1
# (bare REQUIREMENTS/COORDINATES + colon IMPLEMENTATION_STEPS:/ACCEPTANCE:)
# ACTUAL: IllegalStateException: ITR enforcement failed:
#   CU 'cu-001' is missing component 'coordinates'
#   CU 'cu-001' is missing component 'requirements'
```

So partial recognition fails loudly (good) while zero recognition
succeeds silently (bad): the worst combination — one missing colon
anywhere still fails, but all colons missing looks green.

Environment: repo-root `itr-compiler/itr-compiler` assembly, OpenJDK 21
(Temurin-21.0.12+8).

Suspected cause:
`itrcompiler/infrastructure/parsers/JSONFormat.scala`,
`splitInlineSections` matches only the colon labels from
`CU.sectionLabels` (`REQUIREMENTS:` etc.). Zero matches returns
`Map.empty`, and `FileSystem.write` branches on `components.nonEmpty`:
empty takes the legacy single-blob branch with no diagnostic. The
enforcement that catches the partial case never runs for the empty
case. Related: bug-2 (blobs instead of folders) — its colon-label
minimal repro now emits components with this binary, so bug-2's
suspected cause looks stale; this bare-label hole remains.

Impact: Advisor-drafted batches that follow the skill prose but omit
colons compile green into the wrong layout; the error surfaces far
downstream (Coder gets blobs, `verify-cu-structure`/`run-waves`
expect folders).

## Resolution status

Open. No fix merged.

## Fix summary (Resolved only)

Pending. Suggested: either accept bare labels as aliases in
`splitInlineSections`, or emit a warning/failure when `content`
contains a bare near-miss label (`REQUIREMENTS` etc. without colon)
while `components` comes back empty.

## Tests proving resolution (Resolved only)

Pending. Suggested: re-run CLI repro commands 1 and 2 above —
command 1 must either yield the four component files or fail with an
error naming the unrecognized labels (exit non-zero); command 2 must
stay green with four component files. Plus a parser-level test over a
bare-label batch asserting components-or-error instead of silent empty.
