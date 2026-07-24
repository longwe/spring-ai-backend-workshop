#!/usr/bin/env bash
# Regenerates the attendee shell (branch `main`) from the current `solution`
# branch. The shell is `solution` with every workshop:start/end region replaced
# by its TODO stub, and the workshop/ tooling directory removed.
#
# main is always a single fresh orphan commit — push it with:
#   git push -f origin main
set -euo pipefail
cd "$(dirname "$0")/.."

[ "$(git branch --show-current)" = "solution" ] \
  || { echo "error: run this from the solution branch"; exit 1; }
git diff --quiet && git diff --cached --quiet \
  || { echo "error: working tree must be clean"; exit 1; }

# Replace marked regions with stubs, in the working tree.
git grep -l "workshop:start" -- 'src/**' | xargs python3 workshop/strip_regions.py

# Build the shell tree: transformed sources, minus the workshop/ tooling.
git add -A
git rm -rq --cached workshop
tree=$(git write-tree)
commit=$(git commit-tree "$tree" -m \
  "Spring AI workshop shell (generated from solution $(git rev-parse --short solution))")
git branch -f main "$commit"

# Restore the solution working tree.
git reset -q --hard solution

echo "main -> $(git rev-parse --short main) (orphan commit, tree verified clean of solutions)"
echo "publish with: git push -f origin main"
