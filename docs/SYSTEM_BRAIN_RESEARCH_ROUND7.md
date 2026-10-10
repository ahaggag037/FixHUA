# FixHUA 3 — System Brain Research Round 7

## Theme
Device-specific Qualcomm Khaje / Snapdragon 680 control surfaces, bottleneck attribution, and why CPU-only tuning is insufficient.

## 1. Qualcomm Khaje exposes several independent performance domains

Qualcomm's public Khaje reference device tree shows:
- hardware CPU frequency domains (`qcom,cpufreq-hw`)
- LMH-DCVS links on CPU clusters
- DDR DCVS
- bandwidth monitor (`bwmon`) support
- CPU-to-DDR memory-latency monitors (`qcom,memlat`)
- interconnect / BIMC paths
- both eMMC and UFS controller support in the SoC reference design

This is critical: perceived lag can originate from a CPU scheduling delay, CPU frequency ramp, DDR/interconnect latency, GPU work, memory reclaim, storage I/O, or thermal limits. FixHUA must identify the active bottleneck before applying any boost.

## 2. New core component: Bottleneck Attribution Engine

Inputs should be capability-driven and may include:
- CPU runnable delay and runqueue contention
- per-cluster CPU frequency and residency
- scheduler placement / task profile
- memory PSI and reclaim/refault activity
- zRAM/swap page-in/page-out deltas
- DDR/devfreq frequency and bandwidth counters if exposed
- GPU frequency and FrameTimeline jank
- I/O PSI and block-device latency if exposed
- thermal state / LMH throttle evidence

Output categories:
- CPU_STARVED
- CPU_FREQ_RAMP
- MEMORY_RECLAIM
- SWAP_THRASH
- DDR_LATENCY
- GPU_BOUND
- IO_STALL
- BINDER_OR_LOCK_WAIT
- THERMAL_CAP
- OEM_BACKGROUND_POLICY
- UNKNOWN

A single incident may have multiple contributors, with confidence scores rather than a forced single cause.

## 3. New idea: Causal Intervention Gate

No write action should be allowed unless:
1. the relevant bottleneck has been observed repeatedly,
2. the device exposes a reversible control surface,
3. the intervention is bounded,
4. a success metric is defined in advance,
5. rollback is available.

Example: if jank occurs while CPU has spare capacity but GPU is saturated, CPU boosting is rejected automatically.

## 4. DDR is a first-class citizen

The Khaje reference tree contains memory-latency monitors mapping CPU frequencies to DDR bandwidth/frequency targets. This implies CPU-to-memory coordination is intentionally hardware-managed.

FixHUA should therefore first observe the vendor's existing memlat/DCVS behavior. Directly forcing DDR clocks should not be an early optimization because it can increase power/thermal cost and may conflict with vendor governors.

Candidate component: **Memory-Bus Observer**
- discover `/sys/class/devfreq/*` devices
- classify GPU, DDR/interconnect, cache or bus-related nodes
- record governors, current/available frequencies, polling intervals and statistics where readable
- never assume a node's identity from path alone; correlate name/OF compatible/device links

## 5. Storage type must be probed

The Khaje SoC supports both eMMC and UFS. Therefore the SoC model does not tell us which storage technology the phone actually uses.

FixHUA must detect the live block topology and filesystem before making any I/O assumptions. Optimization policy for UFS and eMMC must remain separate.

## 6. Thermal and LMH must remain authoritative

LMH-DCVS is hardware/firmware-assisted limiting. If frequencies are capped despite load, FixHUA should treat that as a constraint signal, not something to bypass.

New metric: **Requested-vs-Delivered Performance Gap**
- workload demand high
- requested policy high
- delivered CPU/GPU/DDR frequency lower than expected
- thermal/LMH evidence present

This identifies thermal or hardware protection as the bottleneck instead of misclassifying it as scheduler failure.

## 7. Capability Probe additions

Read-only probe should inventory:
- `/sys/devices/system/cpu/cpufreq/policy*`
- `/sys/class/devfreq/*`
- `/sys/class/thermal/thermal_zone*`
- `/sys/fs/cgroup` mounts/controllers
- task profile JSON files
- `/proc/pressure/*`
- `/proc/vmstat`
- zRAM sysfs nodes
- block devices + scheduler + filesystem
- Perfetto data-source descriptors
- available ftrace events

Every capability should be recorded as PRESENT / ABSENT / PRESENT_BUT_UNREADABLE / WRITEABLE / UNKNOWN.

## Provisional conclusion

FixHUA should not be a CPU tuner. On this platform family, the likely winning design is a multi-domain controller whose first job is to prove which resource caused latency before it changes anything.

## Sources
- Qualcomm Khaje device tree: https://android.googlesource.com/kernel/msm-extra/devicetree/+/refs/heads/android-msm-redbull-4.19-android13-qpr2-beta/qcom/khaje.dtsi
- Qualcomm Snapdragon 680 product page: https://www.qualcomm.com/smartphones/products/6-series/snapdragon-680-4g-mobile-platform
- Perfetto CPU scheduling: https://perfetto.dev/docs/data-sources/cpu-scheduling
- Perfetto GPU: https://perfetto.dev/docs/data-sources/gpu
