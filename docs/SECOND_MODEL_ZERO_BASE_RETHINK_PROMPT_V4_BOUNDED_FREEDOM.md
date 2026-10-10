# FixHUA 3 — Independent Second-Model Zero-Base Rethink Prompt V4 (Bounded Freedom)

> This prompt is the preferred execution model for the independent second-model review.
>
> Before starting, read:
> - `docs/SECOND_MODEL_ZERO_BASE_RETHINK_PROMPT_V2_CREATIVE.md`
> - `docs/SECOND_MODEL_ZERO_BASE_RETHINK_PROMPT_V3_TIMEBOXED.md`
>
> V2 supplies the full technical, adversarial, research, and creative requirements. V3 supplies the mission-clock idea. **This V4 overrides any V3 rule that restricts useful creative exploration too aggressively.**

---

# 0. PRIME DIRECTIVE

You have two obligations that must coexist:

1. **Finish the mission within a hard 90-minute wall-clock budget.**
2. **Do not let the timebox suppress a genuinely valuable new idea, connection, architecture, mechanism, or reframing.**

The deadline limits **how long you work**, not **how widely you are allowed to think**.

Do not confuse scope discipline with intellectual narrowness.

You are explicitly allowed to:
- invent new mechanisms,
- combine ideas from distant fields,
- overturn the current FixHUA thesis,
- introduce a new subsystem if it directly strengthens the mission,
- discard the existing architecture,
- reopen a decision if a substantially better idea appears,
- follow an unexpected line of reasoning when it has credible high impact,
- create concepts that do not currently exist in the FixHUA research.

You are not required to remain inside the vocabulary, architecture, or assumptions of the existing project.

---

# 1. HARD MISSION CLOCK

The mission starts immediately when this prompt is received.

Record:

```text
MISSION_START = <timestamp or T+00:00>
MISSION_DURATION = 90 minutes
MISSION_DEADLINE = MISSION_START + 90 minutes
```

The mission ends no later than **T+90**.

This deadline is hard. Do not request an extension. Do not continue research after it.

If the best result is ready earlier, stop earlier.

The purpose of the deadline is to prevent endless wandering, not to force premature convergence.

---

# 2. FLEXIBLE TIME ENVELOPE — NOT A CAGE

Use this as a default rhythm, not a rigid schedule:

- roughly T+00–15: orientation, demolition, problem reframing
- roughly T+15–60: deep research, creative divergence, synthesis, invention
- roughly T+60–80: comparison, convergence, architecture formation
- roughly T+80–90: final synthesis and report completion

You may move time between phases when useful.

For example:
- if a breakthrough appears at T+63, pursue it;
- if research converges naturally at T+48, start synthesis early;
- if an architecture collapses at T+72, reopen the design;
- if a late cross-domain connection is likely to materially improve the result, investigate it within the remaining budget.

The only immovable boundary is **T+90**.

---

# 3. CREATIVITY RESERVE

Treat at least **15–20 minutes of the total mission as movable creativity reserve**.

This reserve is not tied to a specific clock window.

Spend it whenever the work benefits from:
- exploring an unexpected analogy,
- combining two apparently unrelated mechanisms,
- challenging a foundational assumption,
- generating a new architecture,
- testing a counterintuitive hypothesis,
- pursuing a high-impact idea discovered late.

Do not spend the creativity reserve merely to produce more ideas. Spend it where there is a plausible path to a stronger design.

Unused creativity reserve may become synthesis time.

---

# 4. EXPLORATION FREEDOM CLAUSE

There is **no fixed maximum number of ideas, domains, architectures, or active conceptual branches**.

Do not artificially stop at 25 ideas, 3 architectures, or any other quota.

V2's creativity targets are minimum prompts for breadth, not ceilings.

You may exceed them freely.

However, every explored branch should eventually receive one of four dispositions:

- `INTEGRATE`
- `TEST_NEXT`
- `REJECT`
- `DEFER`

Creative freedom does not mean leaving every branch unresolved.

---

# 5. BREAKTHROUGH OVERRIDE

V3's previous `NO-NEW-DOMAINS` and strict architecture-freeze rules are replaced by this mechanism.

A new idea may be opened **at any point before the hard deadline** if it plausibly satisfies at least three of the following:

1. It could materially improve user-perceived responsiveness, smoothness, stability, or app survival.
2. It exposes a previously missed causal mechanism.
3. It could simplify the architecture while preserving or increasing value.
4. It creates a new measurable and reversible intervention path.
5. It resolves a major contradiction or uncertainty in the current design.
6. It combines existing mechanisms into something more powerful than the parts.
7. It changes what the first executable milestone should be.
8. It reveals that the current architecture is solving the wrong problem.

If it passes this **Breakthrough Gate**, pursue it even if it appears late.

Do not pursue it merely because it is novel, technically interesting, or intellectually impressive.

---

# 6. SOFT CONVERGENCE, NOT PREMATURE FREEZE

The architecture should become progressively harder to change as the deadline approaches, but never logically impossible to change.

Use these states:

### OPEN EXPLORATION
Early mission. Architecture can change freely.

### PROVISIONAL CONVERGENCE
Once a strong architecture emerges, treat it as the current winner while continuing to attack it.

### CHALLENGER WINDOW
Any new idea that appears must beat the provisional architecture on expected impact, mechanism clarity, measurability, safety, or simplicity.

### FINAL COMMIT
Commit to the strongest coherent architecture when enough evidence exists or when remaining time requires finalization.

There is no mandatory T+70 freeze.

A late breakthrough can replace the current winner if it is clearly superior and can still be explained coherently before T+90.

---

# 7. TWO-MODE THINKING LOOP

Alternate deliberately between two cognitive modes.

## Mode A — Divergent invention
Ask:
- What are we assuming that does not need to be true?
- What if the phone were treated like a control system, market, network, cache hierarchy, real-time system, flight controller, or distributed system?
- Which two weak ideas become powerful when combined?
- What signal exists that nobody is using as a control input?
- What resource conflict is being managed with the wrong abstraction?
- Can we predict a bad state before it occurs rather than react after it?
- Can we improve perceived performance by changing timing rather than magnitude?
- Can we reduce work instead of allocating more resources?
- Can one mechanism solve several apparent problems at once?

## Mode B — Adversarial engineering
For each promising invention ask:
- What exact mechanism makes it work?
- What signal detects the condition?
- What action changes the condition?
- What subsystem may fight or overwrite the action?
- What could make the idea placebo-like?
- What metric shows benefit?
- What metric shows harm?
- What is the rollback?
- What is the runtime overhead?
- What happens on a different firmware/kernel state?

Move repeatedly between Mode A and Mode B.

Do not stay purely creative and do not stay purely conservative.

---

# 8. CROSS-DOMAIN SYNTHESIS IS ENCOURAGED

You are encouraged to borrow useful concepts from fields outside Android when they can be translated into real mechanisms.

Candidate source fields include, but are not limited to:
- control theory
- congestion control
- real-time systems
- operating-system scheduling
- cache economics
- robotics
- aviation fault management
- distributed systems
- queuing theory
- reliability engineering
- online experimentation
- cybernetics
- portfolio/resource allocation
- adaptive signal processing
- anomaly detection
- game theory
- biological homeostasis

Do not import terminology for decoration.

For every borrowed concept, explicitly map:

`foreign concept → Android mechanism → observable signal → possible action → expected user effect → test`

If the mapping is weak, reject it.

---

# 9. IDEA COMBINATION REQUIREMENT

Do not evaluate every idea in isolation.

Actively search for **compositions** where the combination is more valuable than either mechanism alone.

Examples of combination patterns:

- prediction + short-lived intervention
- incident tracing + per-app memory value
- thermal headroom + launch boosting
- PSI triggers + black-box snapshots
- Binder dependency graph + scheduler attribution
- app reload cost + preservation policy
- OEM policy detection + exit-reason classification
- shadow mode + rollback + personalized learning

Create new combinations beyond these examples.

For the strongest candidates, ask whether a single unified mechanism can replace several independent tweaks.

---

# 10. RELEVANCE GUARDRAIL — NOT A TOPIC BAN

A branch is allowed to continue when it can plausibly improve the FixHUA mission.

A branch should be stopped or deferred when:
- its connection to user-visible performance becomes increasingly indirect,
- there is no plausible control surface,
- it requires building an unrelated product first,
- it cannot be measured on the target device,
- its expected effect is tiny relative to complexity,
- it depends on unsupported or imaginary capabilities,
- it becomes interesting mainly as research rather than as a FixHUA mechanism.

This rule should prevent project drift without forbidding creativity.

---

# 11. DRIFT CHECK — ASK FOR VALUE, NOT CONFORMITY

Periodically ask:

- Am I still trying to improve the phone or merely exploring something interesting?
- Could this new direction create a materially better FixHUA?
- Does it reveal a new causal mechanism?
- Does it simplify or strengthen the system?
- Can it eventually become measurable on the real device?

If the answer is strongly positive, **continue even if the idea was not in the original scope map**.

If the answer is weak, defer it.

Do not ask: “Was this idea already planned?”

Novelty relative to the existing plan is not a reason to stop.

---

# 12. LATE-IDEA POLICY

A useful idea discovered near the deadline must not be silently discarded.

If there is enough time to assess and integrate it responsibly, do so.

If there is not enough time, record it in a special section:

`HIGH-POTENTIAL LATE DISCOVERIES`

For each late discovery include:
- core idea,
- why it may matter,
- plausible mechanism,
- what evidence is missing,
- the first test to run next session.

This preserves innovation without violating the hard deadline.

---

# 13. FINALIZATION RULE

During the final portion of the mission, prioritize producing a coherent answer.

But “finalization” does **not** mean your mind is forbidden from noticing a better idea.

If a breakthrough appears during finalization:
- assess it quickly using the Breakthrough Gate;
- integrate it if it materially improves the design and time permits;
- otherwise capture it under `HIGH-POTENTIAL LATE DISCOVERIES`.

Never extend beyond T+90.

---

# 14. WHAT MUST REMAIN BOUNDED

Freedom applies to thinking and invention.

These remain bounded:
- wall-clock duration: 90 minutes maximum
- project objective: FixHUA phone performance/stability/intelligence
- safety constraints
- requirement for causal mechanisms
- requirement for measurable outcomes
- requirement for reversibility for automated privileged interventions
- requirement to distinguish fact, inference, hypothesis, and unknown
- requirement to finish with a coherent recommendation rather than an endless idea list

Everything else may be challenged.

---

# 15. END-OF-MISSION OUTPUT

The final report should still include the substantive deliverables required by V2, plus:

## CREATIVE SYNTHESIS LEDGER
For the strongest novel ideas:
- source concepts combined
- resulting mechanism
- why the combination is non-obvious
- expected impact
- evidence level
- first experiment

## ARCHITECTURE CHALLENGERS CONSIDERED
Show the strongest alternative architectures and why the winner beat them.

## BREAKTHROUGHS THAT CHANGED THE DESIGN
List any idea discovered during the mission that caused a meaningful redesign.

## HIGH-POTENTIAL LATE DISCOVERIES
Preserve high-value ideas that could not be fully investigated before the deadline.

## MISSION FOOTER

```text
MISSION_START:
MISSION_DEADLINE:
MISSION_END:
TIMEBOX_STATUS: ON_TIME | EARLY_STOP | DEADLINE_REACHED

PRIMARY_ARCHITECTURE_SELECTED:
FIRST_EXECUTABLE_MILESTONE:
STRONGEST_NEW_IDEAS:
BIGGEST_IDEAS_REJECTED:
BREAKTHROUGHS_THAT_CHANGED_THE_DESIGN:
HIGH_POTENTIAL_LATE_DISCOVERIES:
BIGGEST_REMAINING_UNCERTAINTIES:
NEXT_SESSION_BACKLOG:
```

---

# FINAL EXECUTION INSTRUCTION

Start immediately.

Work with **maximum intellectual freedom inside a finite mission window**.

Explore aggressively.
Connect distant ideas.
Invent mechanisms that are not in the current research.
Challenge the project from first principles.
Let genuinely strong discoveries change the architecture, even late in the mission.

At the same time, force every promising idea through engineering reality: mechanism, evidence, control surface, measurement, cost, safety, and rollback.

Do not confuse discipline with conformity.
Do not confuse creativity with wandering.

**The deadline is rigid. The thinking is not.**
