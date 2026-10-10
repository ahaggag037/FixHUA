# FixHUA 3 — System Brain Research Round 8

## Theme
Memory-pressure intelligence: PSI, lmkd, zRAM, refault/thrashing, app survival and why "free RAM" is the wrong optimization target.

## 1. Android already defines a better memory-pressure signal than free RAM

Android 10+ can use Pressure Stall Information (PSI) for `lmkd`. PSI measures time lost because tasks are stalled by resource pressure. Android 11's `lmkd` also considers thrashing, swap state, process importance and other signals rather than only free-memory thresholds.

Therefore FixHUA should measure memory health using latency/stall evidence first and memory quantity second.

## 2. New component: Memory Pressure State Machine

Candidate states:
- M0 HEALTHY
- M1 CACHED_GROWTH
- M2 RECLAIM_ACTIVE
- M3 SWAP_ACTIVE_BUT_HEALTHY
- M4 REFAULT_THRASH
- M5 CRITICAL_PRESSURE
- M6 POST_KILL_RECOVERY

Transition evidence can combine:
- PSI `some` and `full` deltas
- `pgscan*`, `pgsteal*`, `pgfault`, `pgmajfault`
- `workingset_refault*` where exposed
- `pswpin` / `pswpout`
- zRAM occupancy and compression ratio
- free/available RAM
- `oom_score_adj` of affected processes
- LMKD events and exit reasons

## 3. Event-driven PSI instead of coarse polling

Linux PSI supports threshold subscriptions via `poll()` / `epoll()`. This allows a privileged daemon to sleep until a real stall threshold is crossed.

Candidate component: **Stall Sentinel**
- low-overhead event subscription
- separate thresholds for memory some/full and I/O some/full
- hysteresis and cooldown
- event timestamp passed to Black Box trace snapshot

Thresholds must be learned from the device baseline. A universal threshold is rejected.

## 4. zRAM is not one feature; capabilities vary by kernel

Old kernels can support basic zRAM and optional writeback. Newer kernels may add access-time tracking and multi-compression/recompression. A 4.19-based device must not be assumed to support modern recompression interfaces.

Capability inventory should record:
- active compressor
- available compressors
- disksize
- `mem_used_total`
- `orig_data_size`
- compression ratio
- `same_pages`, `huge_pages` when available
- `bd_stat` / backing device
- writeback support
- idle tracking support
- multi-compression/recompression support

## 5. New metric: Swap Quality Score

Swap occupancy alone is not bad. The score should ask whether swap is preserving useful app state without causing stalls.

Candidate inputs:
- app reload rate avoided
- swap-in latency / major-fault bursts
- memory PSI before/after
- refault rate
- battery and CPU cost of compression
- storage writeback cost if used

High zRAM occupancy + low PSI + low reload rate can be healthy.
Low occupancy + high reclaim/refault can be unhealthy.

## 6. New component: Reload Cost Model

Protecting every app wastes RAM. For each app learn:
- cold-start time
- warm-start time
- state reconstruction cost
- typical PSS/RSS/swap
- how often user returns within N minutes
- whether background execution is genuinely required

Derived quantity: **State Preservation Value**

A high-value app may deserve memory preservation; a cheap-to-relaunch app should remain killable.

## 7. lmkd should be observed before being tuned

AOSP exposes many lmkd properties, including PSI thresholds, thrashing limit, swap-free threshold and kill strategy. These are powerful but system-wide and can create severe regressions if tuned blindly.

Policy:
- Phase 1: read properties and correlate with real events.
- Phase 2: simulate candidate policy offline from recorded traces.
- Phase 3: only bounded experiments, one parameter at a time, with rollback.
- Never disable `lmkd`.

## 8. App survival is multi-dimensional

An app process can be:
- active and runnable
- cached
- cached and frozen
- background-execution-required
- killed/reconstructed

FixHUA should choose the cheapest state that preserves user experience instead of maximizing process count.

## 9. Safety constraints

Do not:
- force-drop caches as routine optimization
- reset zRAM while normal apps are active
- set extreme swappiness/min_free values globally
- disable LMKD
- force every app into a protected OOM class
- enable zRAM writeback without flash-wear budgeting and evidence

## Provisional conclusion

The Memory Brain should optimize **stall time + reload cost**, not free RAM. It should predict pressure early, preserve only expensive state, and use Android's own pressure model as a guide rather than fight it.

## Sources
- AOSP LMKD: https://source.android.com/docs/core/perf/lmkd
- Linux PSI: https://docs.kernel.org/accounting/psi.html
- Linux zRAM: https://docs.kernel.org/admin-guide/blockdev/zram.html
- Android common-kernel zRAM config: https://android.googlesource.com/kernel/common/+/0892a3e235e5b438f6bfde456b6dd4314d56088c/drivers/block/zram/Kconfig
- Perfetto memory counters: https://perfetto.dev/docs/data-sources/memory-counters
