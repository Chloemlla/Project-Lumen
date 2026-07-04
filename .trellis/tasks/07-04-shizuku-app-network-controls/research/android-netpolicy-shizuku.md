# Android Netpolicy Through Shizuku

## Topic

Determine the safest Shizuku shell command surface for per-app network restriction.

## Findings

- AOSP `NetworkPolicyManagerShellCommand` exposes stable shell commands:
  - `cmd netpolicy add restrict-background-blacklist UID`
  - `cmd netpolicy remove restrict-background-blacklist UID`
  - `cmd netpolicy list restrict-background-blacklist`
- The AOSP implementation maps the blacklist command to `setUidPolicy(uid, POLICY_REJECT_METERED_BACKGROUND)` and removal to `setUidPolicy(uid, POLICY_NONE)`.
- This is Android network policy behavior, not a root firewall. It primarily restricts background metered data for a UID and may vary by Android version / vendor ROM.
- Cross-process or delegated networking through another system UID is not reliably blocked by UID netpolicy alone.
- AOSP mainline did not expose a clearly stable `OPSTR_INTERNET` string in the source lookup performed during this task, so AppOps INTERNET must be treated as a runtime-detected hardening attempt rather than a guaranteed control.
- Full per-app network blocking via iptables/nftables would require root and is out of scope.

## Repo Mapping

- Use the existing `ShizukuCapabilityManager` and `ShizukuShellUserService` shell bridge.
- Persist Project Lumen initiated restrictions in a Room table keyed by package name.
- Store command status and last error because unsupported devices/ROMs must be visible to the user.
- Do not silently claim full firewall behavior when only Android netpolicy was applied.
- Attempt `cmd appops set <package> android:internet ignore` and `cmd appops set <package> INTERNET ignore` for delegated-network hardening; record unsupported results instead of failing the whole UID policy operation.
