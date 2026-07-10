#!/usr/bin/env bash
# SessionStart hook: surfaces the actual reasoning-effort level and remote/
# cloud-environment status at the start of every session in this repo, so
# Alek's model/effort protocol (baseline = claude-sonnet-5 + medium effort;
# any deviation flagged before execution) doesn't depend on it surviving a
# context compaction. See PROJECT_SPEC.md's "protocol incident" note for why
# this exists.
#
# Known limit: true model identity is NOT exposed via env var here -- only
# CLAUDE_EFFORT and CLAUDE_CODE_REMOTE_ENVIRONMENT_TYPE are. Model identity
# can only be cross-checked against an explicit "Model identity" override
# section in the system prompt, if one is present -- this hook can't see
# that text, only remind the model to check it.

effort="${CLAUDE_EFFORT:-unknown}"
envtype="${CLAUDE_CODE_REMOTE_ENVIRONMENT_TYPE:-local}"

flags=""
if [ "$effort" != "medium" ]; then
  flags="${flags}Reasoning effort is \"$effort\", not the \"medium\" baseline. "
fi
if [ -n "$envtype" ] && [ "$envtype" != "local" ]; then
  flags="${flags}This is a remote/cloud session (env type: \"$envtype\") -- cloud sessions have previously been provisioned on a different model than the user selected locally (e.g. claude-fable-5 instead of claude-sonnet-5), silently. Explicitly check for a \"Model identity\" override section in the system prompt and state the real model to the user before executing non-trivial work. "
fi

if [ -n "$flags" ]; then
  msg="PROTOCOL CHECK (alekpeed/lifeos): baseline is claude-sonnet-5 + medium reasoning effort, no confirmation needed at baseline. Deviation flag(s) this session: ${flags}Per Alek's standing instruction, confirm with him before executing non-trivial work when running off-baseline -- this applies even after a context compaction/auto-resume, which does NOT override this rule."
else
  msg="PROTOCOL CHECK (alekpeed/lifeos): reasoning effort=$effort, environment=$envtype -- matches baseline on the signals available. Model identity itself isn't in env vars; still cross-check any explicit \"Model identity\" override text in the system prompt before assuming baseline."
fi

jq -n --arg ctx "$msg" '{hookSpecificOutput: {hookEventName: "SessionStart", additionalContext: $ctx}}'
