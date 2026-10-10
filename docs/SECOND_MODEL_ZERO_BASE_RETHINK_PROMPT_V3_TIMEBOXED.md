# FixHUA 3 — Independent Second-Model Zero-Base Rethink Prompt V3 (Time-Boxed)

> This prompt **supersedes the execution model** of V2 while preserving all of V2's technical, research, adversarial-review, and creative-invention requirements.
>
> Before starting, read:
> `docs/SECOND_MODEL_ZERO_BASE_RETHINK_PROMPT_V2_CREATIVE.md`
>
> Everything in V2 remains required unless this V3 explicitly overrides the workflow or timing.

---

# 0. HARD MISSION CLOCK

This task is **strictly time-boxed**.

## Start

The mission begins **immediately when you receive this prompt**.

At the very first line of your work, record:

- `MISSION_START = <current local timestamp available to you>`
- `MISSION_DURATION = 90 minutes`
- `MISSION_DEADLINE = MISSION_START + 90 minutes`

If an exact wall-clock timestamp is unavailable, use monotonic elapsed time and label it clearly as `T+00:00`.

Do not wait for another confirmation.

## Hard end

The mission must stop at **T+90 minutes**.

This is a **hard stop**, not a suggestion.

You may not extend the deadline because:

- you found another interesting research direction,
- more sources are available,
- another architecture appears worth exploring,
- a new subproblem appeared,
- you want to polish the report further,
- or you believe more time would improve completeness.

At the deadline, stop active research and return the best bounded result available.

Anything incomplete must be moved to a clearly labeled:

`DEFERRED / NEXT-SESSION BACKLOG`

Never silently continue past the deadline.

---

# 1. TIME BUDGET BY PHASE

Use the 90-minute budget as follows.

## Phase A — Orientation and demolition
### T+00 → T+15

Goals:
- Read the existing FixHUA research and V2 creative prompt.
- Identify the strongest assumptions.
- Identify likely wrong assumptions.
- Build a compact problem map.
- Define the user-visible outcomes that matter.

Do not spend this phase writing the final architecture.

Output checkpoint at T+15:
- current thesis
- top uncertainties
- top suspected design errors
- research priorities for the remaining time

---

## Phase B — Focused research + creative divergence
### T+15 → T+60

This is the main exploration period.

Perform:
- independent technical research,
- falsification of existing claims,
- cross-domain analogy mining,
- constraint inversion,
- idea algebra,
- contradiction hunting,
- mechanism discovery,
- architecture alternatives.

You may generate many raw ideas here, but stay inside the mission scope:

**Improve real user-perceived smoothness, responsiveness, app survival, stability, and efficiency on the target Android/Huawei device class.**

Do not drift into unrelated product ideas, generic phone management, security tooling, UI redesign, cloud services, unrelated AI features, or broader Android utilities unless they directly strengthen this mission.

### Exploration cap

At any moment, work on no more than **3 active research branches**.

If a fourth interesting branch appears:
- put it in `BACKLOG`,
- do not pursue it immediately.

### Required creative breadth before convergence

Before T+45, generate at least:
- 25 raw candidate ideas/mechanisms,
- 10 cross-domain analogies,
- 5 second-generation ideas created by combining earlier ideas,
- 3 architecture alternatives that materially differ from each other.

Do not spend too long polishing any raw idea.

### NO-NEW-DOMAINS GATE

At **T+60**, exploration ends.

After T+60:
- do not open a new technical domain,
- do not begin researching a new subsystem,
- do not chase new links unless necessary to resolve a blocking factual contradiction in the final design,
- do not invent a completely new project direction.

New ideas after T+60 go to `DEFERRED / NEXT-SESSION BACKLOG`.

---

## Phase C — Convergence and synthesis
### T+60 → T+80

Now stop widening the search.

Your job is to:
- compare the strongest architecture alternatives,
- reject weak or redundant mechanisms,
- rank ideas by expected user-visible impact,
- select the architecture you would actually build,
- define what should be automatic vs advisory vs expert-only,
- define privilege boundaries,
- define the first executable milestone,
- define measurable success criteria,
- define rollback and kill criteria.

### Mandatory convergence rule

By **T+70**, choose one primary architecture.

You may retain at most:
- one primary architecture,
- one fallback architecture,
- one optional future research path.

Everything else must be rejected, deferred, or absorbed into those paths.

Do not end with a menu of equally plausible architectures.

---

## Phase D — Finalization only
### T+80 → T+90

No new research.
No new architecture.
No new subsystem.
No new speculative branch.

Only:
- verify consistency,
- remove contradictions,
- compress duplication,
- make uncertainty explicit,
- ensure evidence labels are present,
- produce the final report.

At T+90, stop.

---

# 2. SCOPE LOCK

The mission scope is fixed at the start:

> Re-evaluate and redesign FixHUA as a high-impact, device-local Android/Huawei performance and stability system that can use root when justified, while maximizing real user-perceived responsiveness, smoothness, multitasking survival, and stability through measurable, reversible, causally justified mechanisms.

A new idea is **in scope only if** it can answer YES to at least one:

1. Could this materially reduce real UI/app latency?
2. Could this materially reduce avoidable app reloads/exits?
3. Could this materially improve smoothness/jank behavior?
4. Could this materially reduce a known CPU/GPU/memory/I/O/thermal/Binder bottleneck?
5. Could this improve diagnosis enough to enable a high-value intervention?
6. Could this make the optimizer safer, more adaptive, or more measurable?

If not, put it in backlog and move on.

---

# 3. DRIFT DETECTOR

Every 15 minutes, perform a 60-second mission check.

Ask:

- Am I still solving the original FixHUA performance/stability problem?
- Did I start researching something because it is interesting rather than useful?
- Am I expanding scope instead of increasing depth?
- Am I creating features rather than mechanisms?
- Am I adding complexity whose expected user-visible effect is weak?
- Am I duplicating something Android/Huawei already does better?
- Am I spending time proving a low-impact detail while a high-impact uncertainty remains unresolved?

If drift is detected:

1. stop that branch,
2. record one sentence in `DEFERRED / NEXT-SESSION BACKLOG`,
3. return to the highest-impact unresolved question.

---

# 4. RESEARCH PRIORITY RULE

When choosing what to investigate next, use:

`priority = expected_user_impact × uncertainty_reduction × actionability / cost`

Prefer questions that could change the architecture or intervention ranking.

Do not spend significant time resolving trivia that would not change a decision.

---

# 5. CREATIVE SEARCH WITHOUT PROJECT DRIFT

V2 requires aggressive creative synthesis. Keep that requirement, but apply these constraints.

Creative exploration must be **mechanism-oriented** rather than product-feature-oriented.

Good creative question:
- Can a control-theory concept produce better thermal/performance allocation?
- Can cache economics improve app-preservation decisions?
- Can black-box flight recording improve causal attribution?
- Can congestion-control concepts improve memory-pressure intervention?

Bad drift:
- build a launcher,
- add an AI assistant,
- create a general file manager,
- create a cloud dashboard,
- redesign Android,
- build unrelated automation features.

### Novelty is not enough

An idea survives only if it has:
- causal mechanism,
- observable trigger,
- realistic control surface,
- expected user-visible effect,
- measurable success criterion,
- harm metric,
- rollback path,
- acceptable overhead.

A clever idea without these is rejected or deferred.

---

# 6. DECISION FREEZE

To prevent endless redesign:

- T+00–60: architecture may change freely.
- T+60–70: architecture comparison only.
- At T+70: primary architecture is frozen.
- T+70–80: refine the frozen architecture; only change it if a **fatal contradiction** is discovered.
- T+80–90: writing/verification only.

A "better idea" discovered after architecture freeze is not enough to reopen the design.

Only one of these may reopen it:
- safety failure,
- impossible implementation assumption,
- evidence disproving a core mechanism,
- direct contradiction with device/platform constraints.

Otherwise add it to backlog.

---

# 7. OUTPUT BUDGET

The final report must be comprehensive but bounded.

Target:
- 4,000–8,000 words maximum,
- prioritize decisions and mechanisms over literature summary,
- no repeated explanation of the same Android subsystem,
- citations only where they materially support consequential claims.

Do not dump raw research notes into the final report.

---

# 8. MANDATORY END-OF-MISSION STATE

At the end, provide a compact execution footer:

```text
MISSION_START:
MISSION_DEADLINE:
MISSION_END:
TIMEBOX_STATUS: ON_TIME | EARLY_STOP | DEADLINE_REACHED

PRIMARY_ARCHITECTURE_SELECTED:
FIRST_EXECUTABLE_MILESTONE:
TOP_5_HIGHEST_IMPACT_IDEAS:
TOP_5_REJECTED_IDEAS:
BIGGEST_REMAINING_UNCERTAINTIES:
DEFERRED_NEXT_SESSION_BACKLOG:
```

If time runs out before every requested section is perfect, still return the strongest coherent answer and clearly mark what remains unresolved.

Do not continue researching after the deadline.

---

# 9. ANTI-RABBIT-HOLE RULES

You are explicitly forbidden from:

- recursively researching every newly discovered term,
- following citation chains without a decision-relevant reason,
- endlessly comparing equivalent tools,
- exploring unrelated kernel subsystems,
- expanding the project into a general Android operating-system replacement,
- turning the project into an AI-for-everything platform,
- adding cloud/server infrastructure unless it is proven essential,
- optimizing for novelty over impact,
- optimizing synthetic benchmark scores as the main objective,
- continuing because the topic remains intellectually interesting.

The goal is **the strongest bounded design**, not the largest body of research.

---

# 10. DEFINITION OF DONE

The mission is DONE when, before the deadline, you have produced:

1. an independent verdict on the existing research,
2. a falsified/validated set of core assumptions,
3. several genuinely different architecture candidates,
4. a creative idea inventory,
5. a ranked high-impact shortlist,
6. one selected primary architecture,
7. clear privilege and safety boundaries,
8. a read-only capability-probe design or a justified replacement,
9. the first executable milestone,
10. an experimental validation plan,
11. rollback and kill criteria,
12. a bounded backlog for everything not pursued.

Once these exist at sufficient quality, you may stop **before** T+90 rather than filling time.

Do not create extra work merely because time remains.

---

# FINAL EXECUTION INSTRUCTION

Start immediately.
Record the start time.
Calculate the hard deadline.
Read V2 and the FixHUA research.
Work aggressively but within scope.
Diverge early, converge deliberately, freeze the architecture, and stop on time.

**Depth is valuable. Unbounded exploration is not.**
