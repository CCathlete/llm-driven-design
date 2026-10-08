#!/usr/bin/env bash
#
# One-time force-push for the history rewrite that purged assembled fat JARs.
#
#   ./force-push-rewritten-history.sh --dry-run   preview only, changes nothing
#   ./force-push-rewritten-history.sh             do it (asks first)
#   ./force-push-rewritten-history.sh --yes       do it, no prompt
#
# WHY THIS IS NEEDED
#   git-filter-repo rewrote every commit to drop these paths:
#     dtr-builder/dtr-builder            (21.7 MB)
#     commit-tool/commit-tool             ( 6.1 MB)
#     itr-compiler/itr-compiler           ( 6.1 MB)
#     dtr-builder/dtr-builder-0.1.1.jar   (20.8 MB)
#   Rewriting changes every commit SHA, so the remote and local histories are
#   now unrelated. Only a force-push can reconcile them.
#
# WHAT BREAKS
#   Anyone else who has cloned this repo must re-clone or hard-reset:
#     git fetch origin && git reset --hard origin/main
#   Their existing branches will not merge cleanly with rewritten history.

set -euo pipefail

EXPECTED_SLUG="CCathlete/llm-driven-design"
DRY_RUN=0
ASSUME_YES=0
NO_VERIFY=()

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    --yes|-y)   ASSUME_YES=1 ;;
    --no-verify) NO_VERIFY=(--no-verify) ;;
    -h|--help)  sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

cd "$(git rev-parse --show-toplevel)"

# ── guards ────────────────────────────────────────────────────────────────
origin_url="$(git remote get-url origin 2>/dev/null || true)"
case "$origin_url" in
  *"$EXPECTED_SLUG"*) ;;
  *) echo "ABORT: origin is '${origin_url:-<none>}', expected it to contain '$EXPECTED_SLUG'" >&2; exit 1 ;;
esac

# Only tracked-file changes matter here: a force-push sends commits, and
# untracked files are never transmitted. Ignore them so this script can live
# in the repo without tripping its own guard.
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "ABORT: tracked files have uncommitted changes. Commit or stash first." >&2
  git status --short --untracked-files=no
  exit 1
fi

# Sanity: no blob over 1 MB should be reachable, or the purge did not finish.
# Scoped to local branches and tags on purpose. refs/remotes/origin/* still
# point at the pre-rewrite history until this script's force-push lands, so
# including them here would always trip the guard before the push.
big_blob="$(git rev-list --objects --branches --tags \
  | git cat-file --batch-check='%(objecttype) %(objectsize) %(rest)' \
  | awk '$1=="blob" && $2>1048576 {print $3; exit}')"
if [ -n "$big_blob" ]; then
  echo "ABORT: found a >1MB blob still reachable: $big_blob" >&2
  echo "       The history rewrite may be incomplete. Do not force-push." >&2
  exit 1
fi

# ── report ────────────────────────────────────────────────────────────────
echo "repo   : $(pwd)"
echo "origin : $origin_url"
echo "commits: $(git rev-list --branches --tags --count) on local branches+tags"
echo "         ($(git rev-list --remotes --count) more reachable via refs/remotes/origin/*,"
echo "          the pre-rewrite history — unreachable from here once the push lands)"
echo ".git   : $(du -sh .git | cut -f1) (still holds pre-rewrite objects; reclaimed on --gc)"
echo
echo "branches to force-push:"
git for-each-ref --format='  %(refname:short)  %(objectname:short)  %(contents:subject)' refs/heads/
echo
echo "tags to force-update:"
git for-each-ref --format='  %(refname:short)  %(objectname:short)' refs/tags/
echo

if [ "$DRY_RUN" -eq 1 ]; then
  echo "=== DRY RUN — no remote changes ==="
  git push --dry-run --force "${NO_VERIFY[@]+"${NO_VERIFY[@]}"}" origin \
    'refs/heads/*:refs/heads/*' 'refs/tags/*:refs/tags/*' 2>&1 | sed 's/^/  /'
  echo
  echo "Re-run without --dry-run when this looks right."
  exit 0
fi

echo "!! This rewrites published history. Collaborators must re-clone. !!"
echo
if [ "$ASSUME_YES" -eq 0 ]; then
  read -r -p "Type FORCE-PUSH to proceed: " answer
  [ "$answer" = "FORCE-PUSH" ] || { echo "aborted"; exit 1; }
fi

# ── push ──────────────────────────────────────────────────────────────────
echo
echo "=== pushing branches + tags ==="
git push --force "${NO_VERIFY[@]+"${NO_VERIFY[@]}"}" origin \
  'refs/heads/*:refs/heads/*' 'refs/tags/*:refs/tags/*'

# ── verify ────────────────────────────────────────────────────────────────
echo
echo "=== verifying remote ==="
git fetch origin --prune -q --tags
fail=0
for b in $(git for-each-ref --format='%(refname:short)' refs/heads/); do
  local_sha="$(git rev-parse "$b")"
  remote_sha="$(git rev-parse "origin/$b" 2>/dev/null || echo MISSING)"
  if [ "$local_sha" = "$remote_sha" ]; then
    printf '  OK      %-38s %s\n' "$b" "${local_sha:0:8}"
  else
    printf '  MISMATCH %-37s local=%s remote=%s\n' "$b" "${local_sha:0:8}" "${remote_sha:0:8}"
    fail=1
  fi
done
# Tags are NOT fetched into refs/remotes/origin/*, so `git rev-parse origin/$t`
# always fails and would report a false MISMATCH. Ask the remote directly.
remote_tags="$(git ls-remote --tags origin | awk '{print $2" "$1}')"
for t in $(git for-each-ref --format='%(refname:short)' refs/tags/); do
  local_sha="$(git rev-parse "$t")"
  remote_sha="$(printf '%s\n' "$remote_tags" | awk -v r="refs/tags/$t" '$2==r {print $1}')"
  remote_sha="${remote_sha:-MISSING}"
  if [ "$local_sha" = "$remote_sha" ]; then
    printf '  OK      %-38s %s\n' "$t" "${local_sha:0:8}"
  else
    printf '  MISMATCH %-37s local=%s remote=%s\n' "$t" "${local_sha:0:8}" "${remote_sha:0:8}"
    fail=1
  fi
done

echo
if [ "$fail" -eq 0 ]; then
  echo "All branches and tags match the remote."
  echo
  echo "Verify the repo size dropped at: https://github.com/$EXPECTED_SLUG"
  echo
  echo "NOTE: the module fat JARs are no longer in git. Rebuild locally if needed:"
  echo "  cs launch sbt -- assembly    (in the module dir, then cp the binary to the module root)"
else
  echo "Some refs did not land. Inspect above before retrying." >&2
fi

# Reclaim space regardless of the report above: a false MISMATCH must not
# leave ~27 MB of unreachable objects on disk.
if [ "$fail" -eq 0 ]; then
  echo
  echo "=== reclaiming pre-rewrite objects ==="
  git reflog expire --expire=now --all
  git gc --prune=now --quiet
  echo ".git now: $(du -sh .git | cut -f1)"
fi
exit "$fail"
