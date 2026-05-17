# eval-harness

**Evaluation framework for migration-harness** — Compare model performance, identify skill gaps, and assess migration quality.

Uses Claude CLI for powerful, deep evaluation insights.

---

## Quick Start

```bash
cd ~/eval-harness

# Evaluate single migration
bin/eval-harness /path/to/migrated-repo

# Compare multiple migrations
bin/eval-harness --compare /path/to/repo1 /path/to/repo2 /path/to/repo3
```

---

## What It Does

### Single Evaluation

Reads migration artifacts and generates comprehensive quality report:

**Input (from repo):**
- `metrics.json` — Model, timing, step outcomes
- `PLAN.md` — Migration plan
- `execution-log.md` — Execution progress
- `verification-report.md` — Build/test results

**Output:**
- `<repo>/eval-report.md` — Deep quality assessment with scoring

**Scoring:**
- **Plan Quality** (0-10): Completeness, detail, ordering, reference usage
- **Execution Quality** (0-10): Lessons documented, errors logged, scope discipline
- **Verification Success** (0-10): Build status, test results, auto-fix effectiveness
- **Overall** (0-10): Weighted score + production readiness

### Comparative Evaluation

Compares multiple migrations to identify best model and skill gaps:

```bash
bin/eval-harness --compare \
  ~/eval/coolstore-sonnet-4-5/ \
  ~/eval/coolstore-gpt-4o/ \
  ~/eval/coolstore-gemini/
```

**Output:**
- `eval-runs/eval-comparison-<timestamp>.md`

**Includes:**
- Model rankings (by score, time, success rate)
- Dimension-by-dimension comparison
- Best model analysis (speed vs quality trade-offs)
- Skill improvement recommendations

---

## Usage

### Evaluate Single Migration

```bash
bin/eval-harness /Users/hitpatel/c-sharp-analyzer-provider/testdata/nerd-dinner
```

Output:
```
── Evaluating Migration ──
ℹ Repository: /Users/hitpatel/c-sharp-analyzer-provider/testdata/nerd-dinner
ℹ Model: claude-opus-4-6 (gcp_vertex_ai)
ℹ Duration: 874s (14m)
ℹ Status: success

ℹ Calling Claude CLI for evaluation...
✓ Evaluation complete
✓ Report: /Users/hitpatel/c-sharp-analyzer-provider/testdata/nerd-dinner/eval-report.md
```

### Compare Multiple Migrations

```bash
bin/eval-harness --compare ~/eval/coolstore-*
```

---

## Workflow: Testing Models

```bash
# Step 1: Prepare test copies
cp -r ~/apps/original-app ~/test/app-copy-1
cp -r ~/apps/original-app ~/test/app-copy-2
cp -r ~/apps/original-app ~/test/app-copy-3

# Step 2: Migrate with different models
cd ~/migration-harness
migration-harness init  # Configure for model A
migration-harness ~/test/app-copy-1 "Migrate to X"

migration-harness init  # Configure for model B
migration-harness ~/test/app-copy-2 "Migrate to X"

migration-harness init  # Configure for model C
migration-harness ~/test/app-copy-3 "Migrate to X"

# Step 3: Rename for clarity
mv ~/test/app-copy-1 ~/eval/app-sonnet-4-5
mv ~/test/app-copy-2 ~/eval/app-gpt-4o
mv ~/test/app-copy-3 ~/eval/app-gemini

# Step 4: Compare
cd ~/eval-harness
bin/eval-harness --compare ~/eval/app-*
```

---

## Evaluation Criteria

### Plan Quality (0-10)

- **Completeness** (0-3): All necessary changes identified?
- **Detail** (0-3): Steps specific and actionable?
- **Ordering** (0-2): Dependencies respected?
- **Reference usage** (0-2): Appropriate migration reference used?

### Execution Quality (0-10)

- **Lessons documented** (0-3): Clear lessons per step?
- **Errors logged** (0-3): Errors documented with context?
- **Scope discipline** (0-2): Focused, no over-engineering?
- **Files touched** (0-2): Only necessary files modified?

### Verification Success (0-10)

- **Build status** (0-5): Build succeeds?
- **Test results** (0-3): Test pass rate?
- **Auto-fix effectiveness** (0-2): Auto-fix resolved errors?

### Overall (0-10)

Weighted average:
- Plan Quality: 30%
- Execution Quality: 20%
- Verification Success: 50%

**Production readiness:**
- 9-10: Production ready
- 7-8: Near production ready
- 5-6: Significant work remaining
- 0-4: Not production ready

---

## Use Cases

### 1. Model Selection

Test 3-5 models on same migration to find best performer.

### 2. Skill Improvement

Run eval on 5 different repositories with same model to identify common failures.

### 3. Regression Testing

Re-run eval after updating skills to validate improvements.

### 4. Migration Complexity Analysis

Compare simple vs complex migrations to calibrate expectations.

---

## Example Report

```markdown
# Migration Evaluation Report

**Model:** claude-opus-4-6
**Duration:** 874s (14.6 minutes)
**Status:** ✅ Success

## Scores

| Dimension | Score | Notes |
|-----------|-------|-------|
| Plan Quality | 9/10 | Comprehensive, well-ordered |
| Execution Quality | 6/10 | Sparse lesson logging |
| Verification Success | 10/10 | Build clean |
| **Overall** | **8.5/10** | **Production ready** |

## Skill Gaps
- execute.yaml: Needs stronger lesson prompting
- dotnet-framework-to-core.md: Missing HttpClient patterns
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `claude CLI not found` | Ensure Claude CLI is installed and in PATH |
| `Missing metrics.json` | Run migration-harness on repo first |
| `jq not found` | Install: `brew install jq` |

---

## License

MIT
