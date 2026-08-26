---
name: iss-development-workflow
description: Classify and orchestrate Interview Scheduler System requests across feature discovery, architecture, diagnosis, implementation, review, repair loops, and verification. Use for feature recommendations, non-trivial source changes, bugs or failures, refactors, and reviews; do not use the full pipeline for simple explanations or read-only repository questions.
---

# Automated Development Workflow

The parent Codex agent is the ORCHESTRATOR.

The parent should preserve the original user request throughout the
workflow and return only the consolidated result to the user.

Classify the request before selecting specialists. Explicit user intent
takes precedence over automatic classification.

## Request Classification

Choose one primary category:

1. `FEATURE_DISCOVERY`
2. `NEW_FEATURE`
3. `BUG_OR_FAILURE`
4. `REFACTOR_OR_ARCHITECTURE`
5. `REVIEW_ONLY`
6. `EXPLANATION_OR_READ_ONLY`
7. `SMALL_CODE_FIX`

## Routing

### Feature discovery

For requests asking what to build, what is missing, or what should be
prioritized, spawn only `feature-strategist`. Require repository
inspection, return recommendations to the user, and do not implement a
recommendation without explicit authorization.

```text
User → Orchestrator → Feature Strategist → User
```

If the user explicitly asks to recommend and implement the best option,
use:

```text
Feature Strategist → Architect → Implementer → Reviewer → Final Verification
```

The strategist chooses what should be built; the architect decides how.

### User-selected new feature

When the user has already selected the feature, do not invoke the
strategist unless product requirements are materially unclear. Use:

```text
Architect → Implementer → Reviewer → Final Verification
```

### Bug, failure, or error

Start with `diagnostician`. It must investigate evidence and report
`IMPLEMENTATION READY`, `REPOSITORY CHANGE REQUIRED`, and
`ARCHITECT REVIEW REQUIRED` before source changes begin.

- If `IMPLEMENTATION READY = NO`, do not let the implementer guess.
  Gather more safe read-only evidence when possible, then ask the
  diagnostician to re-evaluate or report what remains missing.
- If implementation is ready but no repository change is required,
  return the root cause, corrective action, and verification procedure.
  Do not expose secrets and do not invoke the implementer unnecessarily.
- If a repository change is ready and architecture review is not
  required, use `Diagnostician → Implementer → Reviewer → Final
  Verification`.
- If architecture review is required, use `Diagnostician → Architect →
  Implementer → Reviewer → Final Verification`.

Give the implementer the original problem, evidence, root cause,
recommended fix, and verification plan. Give the architect the same
diagnostic report when architecture review is required.

### Refactor or architecture change

Use:

```text
Architect → Implementer → Reviewer → Final Verification
```

Do not invoke the strategist unless the user is asking whether the work
is valuable from a product perspective.

### Review only

Use only `reviewer`. Keep the review read-only and return findings to the
user. Do not fix findings unless the user explicitly requested review
and repair.

For review and repair, stop if there are no BLOCKER or MAJOR findings.
Otherwise use `Reviewer → Implementer → Reviewer` when architecture
review is not required, or `Reviewer → Architect → Implementer →
Reviewer` when it is.

### Explanation or read-only analysis

The parent may answer directly or delegate to an appropriate read-only
specialist. Do not invoke the full pipeline. Prefer `diagnostician` when
the request specifically asks why something fails, even if no fix is
requested.

### Small code fix

For a confirmed, clearly local correction where architectural analysis
adds no value, use `Implementer → Reviewer`. When the cause is not
certain, use `Diagnostician → Implementer → Reviewer`.

## Specialist Boundaries

- `feature-strategist` decides what is valuable and why; it does not
  design implementation architecture or modify code.
- `architect` designs selected requirements, boundaries, business rules,
  transactions, and security; it does not normally modify production
  code.
- `diagnostician` determines why something is broken and the smallest
  evidence-based fix; it does not modify files.
- `implementer` owns approved repository modifications and must not
  silently redesign the solution.
- `reviewer` independently determines whether the implementation is
  correct; it does not implement its own findings.

Do not allow role drift. Production source changes should normally be
owned only by `implementer`.

## Step 0 — Safety

Before implementation:

1. Inspect `git status --short`.
2. Identify pre-existing uncommitted changes.
3. Preserve unrelated user work.
4. Never use destructive Git commands on unrelated changes.
5. Do not automatically commit.
6. Do not automatically push.

The user request is the authoritative product requirement.

## Architecture Stage

Run this stage only when selected by the route above.

Spawn the custom agent named `architect`.

Give the architect:

- complete original user request
- relevant repository context
- applicable constraints
- relevant documentation

Require the architect to inspect the actual repository.

Wait for the architect to complete its Task Packet. Do not run architect
and implementer simultaneously.

The architect's output becomes the technical Task Packet for the
implementation phase.

## Implementation Stage

Run this stage only when selected by the route above and after any
required strategy, diagnosis, or architecture work is complete.

After all prerequisite stages selected by the route complete, spawn
`implementer`.

Give the implementer:

- complete original user requirement
- feature-strategist, diagnostician, or architect handoffs that apply
- relevant repository constraints
- warning about any pre-existing working-tree changes

Require the implementer to inspect the real code before editing.

Allow the implementer to:

- edit required files
- add or update tests
- run relevant commands and tests
- inspect its diff

Wait for implementation to finish. Do not start review before
implementation is complete.

## Review Stage

Run this stage after implementation, or by itself for review-only
requests.

After implementation completes, spawn `reviewer`. For a review-only
request, spawn it directly without requiring an implementation stage.

Give the reviewer:

- original user requirement
- applicable strategist, diagnostician, or architect handoffs
- implementer report, when implementation occurred
- current git diff
- test results

Require the reviewer to inspect the actual changed files and repository.
Wait for the reviewer.

## Handle Review Verdict

### APPROVED

Proceed to Final Verification.

### APPROVED WITH MINOR CHANGES

Do not automatically implement OPTIONAL suggestions.

Only implement a MINOR finding automatically when it is clearly
necessary to satisfy the original requirement or acceptance criteria.
Otherwise report it as a recommendation and proceed.

### CHANGES REQUIRED

Inspect `ARCHITECT REVIEW REQUIRED`.

If it is NO, send only BLOCKER and MAJOR findings back to `implementer`.
Require the implementer to inspect each finding, fix valid findings,
avoid unrelated changes, rerun relevant tests, and return an updated
implementation report. Then spawn `reviewer` again.

If it is YES, send the review findings back to `architect`. Require the
architect to reconsider only the affected architecture and produce a
revised Task Packet. Then send the revised Task Packet, reviewer
findings, and original requirement to `implementer`. Require
implementation and tests, then run `reviewer` again.

## Repair Limit

Maximum automatic repair cycles: 2.

One repair cycle means:

```text
Reviewer → Implementer → Reviewer
```

or:

```text
Reviewer → Architect → Implementer → Reviewer
```

If BLOCKER or MAJOR issues remain after two repair cycles, stop changing
code and report the unresolved problems to the user. Do not continue
speculative repairs indefinitely.

## Final Verification

Once the reviewer approves:

1. Inspect `git status --short`.
2. Inspect the final git diff.
3. Confirm unrelated pre-existing changes were preserved.
4. Confirm required tests were executed.
5. Confirm tests passed.
6. Verify acceptance criteria.
7. Check for accidental debug code.
8. Check for accidental secrets.
9. Check for unrelated formatting or refactors.
10. Do not commit or push unless explicitly requested.

## Read-Only and Small-Change Exceptions

Do not invoke the full development workflow for:

- explaining code
- locating a class
- answering a repository question
- reading logs
- describing an error without changing code
- typo-only documentation corrections
- simple comments
- read-only analysis

For small but real source-code fixes, follow the small-code-fix route.
When uncertain about the cause, use the diagnostician first. When
uncertain whether a behavioral change is architecturally trivial, use
the architect.

## Architecture Escalation Rules

The architect must be involved when changes affect:

- entity relationships
- schema design
- module boundaries
- authentication
- authorization
- transaction design
- concurrency strategy
- major dependencies
- public API contracts
- cross-module business rules
- major refactors

## Parallelism Rules

Do not parallelize dependent write-heavy phases.

Never run Architect + Implementer or Implementer + Reviewer
simultaneously for the same change.

Parallel subagents may be used for independent read-only exploration
when useful. Avoid multiple agents simultaneously editing overlapping
source files.

## Final Parent Response

The parent orchestrator should return a concise consolidated report:

## RESULT

What was accomplished.

## ARCHITECTURE

Important decisions from the architect.

## IMPLEMENTATION

What changed.

## FILES CHANGED

Relevant files.

## TESTS

Commands and results.

## REVIEW

Final reviewer verdict.

## ACCEPTANCE CRITERIA

Satisfied or remaining issues.

## REMAINING ISSUES

Only real unresolved items.

## RECOMMENDED NEXT STEP

One sensible next step if applicable.

Do not dump raw subagent conversations unless the user asks for them.
