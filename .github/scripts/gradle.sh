#!/usr/bin/env bash
# Runs ./gradlew with the given arguments. On failure, turns the useful part of the output into
# GitHub annotations so errors show on the run summary and on pull requests without opening logs:
#   - Kotlin compiler errors   ("e: file:///…/File.kt:12:5 message")
#   - Android lint errors      ("app/src/…/File.kt:12: Error: message [Id]")
#   - Gradle's "What went wrong" block
set -uo pipefail

log="$(mktemp)"
./gradlew "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
[ "$status" -eq 0 ] && exit 0

# Annotation messages are single-line; GitHub decodes %0A as a newline.
escape() { sed -e 's/%/%25/g' -e 's/\r//g' | awk 'BEGIN{ORS="%0A"} {print}'; }
workspace="${GITHUB_WORKSPACE:-$PWD}"

# Kotlin: one annotation per error (GitHub shows the first 10 per step), plus all of them in one.
grep -E '^e: ' "$log" | sort -u | head -10 | while IFS= read -r line; do
  msg="${line#e: }"
  if [[ "$msg" =~ ^file://([^:]+):([0-9]+):([0-9]+)\ (.*)$ ]]; then
    file="${BASH_REMATCH[1]#"$workspace"/}"
    echo "::error file=${file},line=${BASH_REMATCH[2]},col=${BASH_REMATCH[3]}::$(printf '%s' "${BASH_REMATCH[4]}" | escape)"
  else
    echo "::error::$(printf '%s' "$msg" | escape)"
  fi
done
kotlin_all="$(grep -E '^e: ' "$log" | sort -u | sed "s#file://$workspace/##" | head -200)"
[ -n "$kotlin_all" ] && echo "::error title=Kotlin compile errors::$(printf '%s\n' "$kotlin_all" | escape)"

# Lint (text report is printed to stdout, see lint { textOutput } in app/build.gradle.kts).
lint_all="$(grep -E ':[0-9]+: Error: ' "$log" | sort -u | head -100)"
[ -n "$lint_all" ] && echo "::error title=Android lint errors::$(printf '%s\n' "$lint_all" | escape)"

# Gradle's own explanation (dependency resolution, plugin, configuration failures).
went_wrong="$(awk '/What went wrong:/{f=1} /^\* Try:/{f=0} f' "$log" | head -80)"
[ -n "$went_wrong" ] && echo "::error title=Gradle: what went wrong::$(printf '%s\n' "$went_wrong" | escape)"

exit "$status"
