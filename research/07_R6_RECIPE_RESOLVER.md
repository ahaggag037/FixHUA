# R6 — Compatibility Recipe + Deterministic Resolver

Status: GATE DECISION READY
Branch: `research/synthetic-system-v1`
Date: 2026-10-11

## Goal

Convert the Capability Graph, bootstrap planes, Synthetic Runtime and Google providers into a deterministic execution plan. No app-specific behavior may require ad-hoc code branches when it can be represented as recipe data.

## Core model

FixHUA resolves **requested outcomes**, not named products.

```text
User / target app outcome
        ↓
Capability requirements
        ↓
Device + runtime facts
        ↓
Candidate providers
        ↓
Dependency expansion
        ↓
Safety/truth/authority gates
        ↓
Deterministic provider plan
        ↓
Execution + evidence
```

## Four truths that must never be merged

Every provider result carries a `truthScope`:

- `HOST_REAL` — fact/action is true on the real Huawei host.
- `HOST_DELEGATED` — real host effect performed through an authorized elevated provider.
- `SYNTHETIC_VIEW` — true only inside a managed FixHUA runtime.
- `GUEST_REAL` — true inside a contained guest/root bubble, not on the host.

A provider producing `SYNTHETIC_VIEW` can never satisfy a requirement explicitly marked `HOST_REAL_REQUIRED`.

## Capability provider contract

Each backend registers a descriptor:

```text
ProviderDescriptor
- providerId
- providerVersion
- authorityPlane: P0..P7
- produces: [capability IDs]
- requires: [capability IDs]
- truthScopes: [...]
- semantics: EXACT | EQUIVALENT | DEGRADED | FALLBACK_ONLY
- persistence: STATIC | USER_GRANTED | BOOTSTRAPPED | LIVE_BROKER | SESSION
- destructive: boolean
- consent: NONE | ONCE | PER_ACTION
- deviceConstraints
- runtimeConstraints
- evidenceType
```

Examples:

- Android public launcher → P0 / HOST_REAL / EXACT.
- Usage Access → P1 / HOST_REAL / USER_GRANTED.
- WRITE_SECURE_SETTINGS bootstrap → P2 / HOST_DELEGATED / BOOTSTRAPPED.
- shell Binder backend → P3 / HOST_DELEGATED / LIVE_BROKER.
- Huawei documented settings route → P4 / HOST_REAL or HOST_DELEGATED.
- Synthetic PackageManager → P5 / SYNTHETIC_VIEW.
- guest root → P6 / GUEST_REAL.

## Recipe contract

A recipe does not contain executable code. It declares:

- target selectors;
- desired outcomes / required capabilities;
- allowed truth scopes;
- provider preferences or exclusions;
- shims and their semantics;
- dependencies;
- fallbacks;
- integrity boundaries;
- version/device applicability;
- evidence requirements.

Recipes are versioned and validated before activation.

## Shim types

Only the following are accepted in v0.1:

- `PASS_THROUGH`
- `SYNTHETIC_VALUE`
- `SAFE_STUB`
- `REDIRECT_SERVICE`
- `FILTER_FLAGS`
- `SHAPE_RESULT`
- `DISABLE_SUBFEATURE`
- `VIRTUAL_IDENTITY`
- `HOST_DELEGATE`
- `UNSUPPORTED_INTEGRITY_BOUNDARY`

There is intentionally no generic `RETURN_SUCCESS` shim.

## Resolver algorithm v0.1

For each required capability:

1. Load all provider descriptors that claim the capability.
2. Filter providers that fail:
   - current Android / EMUI / ABI constraints;
   - user consent state;
   - requested truth scope;
   - recipe exclusions;
   - integrity boundary policy;
   - required runtime availability.
3. Expand provider dependencies recursively.
4. Reject cycles.
5. Reject any plan whose dependency cannot be resolved.
6. Rank surviving plans lexicographically, not by opaque ML score:
   1. semantic correctness (`EXACT` > `EQUIVALENT` > `DEGRADED` > `FALLBACK_ONLY`);
   2. truth-scope match;
   3. lower privilege tier;
   4. non-destructive over destructive;
   5. less user friction (`STATIC`/existing grant > bootstrap > live broker > per-action consent);
   6. lower runtime overhead;
   7. recipe preference.
7. Produce an immutable `ResolutionPlan`.
8. Run precondition checks immediately before execution.
9. Execute each step through its backend.
10. Attach evidence and actual outcome; never convert planned success into verified success without backend evidence.

## Compound capabilities

A user-visible feature may require multiple capabilities.

Example: "run app with Google-compatible location":

```text
CAP-52 runtime route
  + CAP-59 virtual PM
  + CAP-68 virtual identity
  + Google VCI
  + location provider
```

The resolver chooses a coherent set, not four independent providers whose assumptions conflict.

## Provider compatibility groups

Recipes may declare a `runtimeGroup` to prevent impossible mixtures.

Example groups:

- `HOST_NATIVE`
- `TSR_INSTANCE:<id>`
- `GBOX_INSTANCE`
- `GUEST_INSTANCE:<id>`

A virtual identity from TSR cannot silently satisfy a service call made from a normal host-installed app.

## Virtual Caller Identity gate

Any provider that serves a managed target and depends on caller identity must declare:

`requiresVirtualCallerIdentity: true`

The resolver then requires the request to execute inside the same managed runtime group as the VCI broker. This prevents the real FixHUA host UID from being mistaken for the target UID.

## Safety gates

### Destructive actions

Capabilities such as clear-data or uninstall require explicit per-action user approval even if a P3/P4 provider is available.

### Synthetic truth

UI must show when a result is synthetic. Logs store both host observation and synthetic result where applicable.

### Integrity

If a target requests an integrity-sensitive feature whose correctness depends on trusted hardware/host state, resolver selects `UNSUPPORTED_INTEGRITY_BOUNDARY` or a legitimate product fallback. It does not spoof a successful attestation.

### Safe stubs

A `SAFE_STUB` must declare:

- expected caller semantics;
- neutral return value;
- known side-effect expectations;
- evidence/source for safety.

Missing safety metadata makes the recipe invalid.

## Evidence model

Every executed plan step emits:

```text
ExecutionEvidence
- timestamp
- targetRuntimeId
- targetVirtualIdentity
- capabilityId
- providerId + version
- authorityPlane
- truthScope
- requestedOperation
- actualResult: SUCCESS | PARTIAL | DENIED | UNSUPPORTED | ERROR
- evidenceSummary
- hostChanged: true/false
- syntheticChanged: true/false
```

Sensitive raw data remains local unless explicitly shared by the user.

## Failure behavior

Provider failure does not automatically advance to a more privileged backend.

Fallback occurs only if:

- the recipe explicitly allows it;
- semantics remain acceptable;
- no destructive escalation occurs silently;
- the new truth scope still satisfies the outcome.

Example: failure of native launch may allow GBox fallback. Failure of a synthetic setting must not automatically trigger a host secure-setting write.

## R6 implementation interfaces

Planned core interfaces:

```text
CapabilityProvider
  describe()
  probe(context)
  preflight(request)
  execute(request)

CapabilityResolver
  resolve(request, deviceProfile, recipes, providerRegistry)

RecipeStore
  validate(recipe)
  select(target, deviceProfile)
  versions()

EvidenceStore
  append(evidence)
  query(target/capability/provider)
```

## R6 gate result

**PASS for architecture.**

The schema and algorithm are sufficiently bounded to freeze the resolver contract before code implementation. The JSON schema file is the machine-readable v0.1 contract.

## Next gate: R7

Freeze MVP architecture:

- Android modules / packages;
- persistent stores;
- IPC boundaries;
- bootstrap interface;
- TSR process model;
- provider registry;
- CI/test matrix;
- first vertical slice acceptance tests.

After R7, broad research stops and implementation becomes the default activity.
