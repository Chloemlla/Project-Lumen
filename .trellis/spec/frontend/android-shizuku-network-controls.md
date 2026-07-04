# Android Shizuku Network Controls

> Contracts for developer-only app network policy management through Shizuku.

---

## Scenario: Developer App Network Controls

### 1. Scope / Trigger

- Trigger: any change to developer app network controls, Shizuku package inventory, `cmd netpolicy`, AppOps INTERNET hardening, or the `app_network_controls` Room table.
- Scope: Project Lumen may provide developer-only controls to restrict installed user/system apps through Shizuku, but must not describe Android UID policy as a root firewall.

### 2. Signatures

```text
cmd package list packages -3 -i -U --show-versioncode
cmd package list packages -s -i -U --show-versioncode
cmd netpolicy add restrict-background-blacklist <uid>
cmd netpolicy remove restrict-background-blacklist <uid>
cmd netpolicy list restrict-background-blacklist
cmd appops set <packageName> android:internet ignore|allow
cmd appops set <packageName> INTERNET ignore|allow
```

```kotlin
data class AppNetworkControlEntity(
    @PrimaryKey val packageName: String,
    val uid: Int,
    val appType: String,
    val networkRestricted: Boolean,
    val uidPolicyApplied: Boolean,
    val delegatedGuardAttempted: Boolean,
    val delegatedGuardApplied: Boolean,
    val lastCommandOutput: String,
    val lastError: String,
    val restrictedAt: Long,
    val restoredAt: Long,
    val updatedAt: Long,
)
```

### 3. Contracts

- Shizuku must be binder-available and authorized before package discovery or network policy commands run.
- Package names used in shell commands must match the project package-name regex before command execution.
- The app must not expose a free-form shell command field for this feature.
- UID network restriction uses `cmd netpolicy add restrict-background-blacklist <uid>` and restore uses `remove restrict-background-blacklist <uid>`.
- AppOps INTERNET is a best-effort delegated-network guard only; unsupported AppOps commands must be persisted as unsupported, not hidden.
- Persist every Project Lumen initiated restrict/restore attempt with package name, UID, app type, command status, and last error/output.
- Restore is idempotent: a system response such as `not blacklisted` or `not denylisted` is treated as restored.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Shizuku binder unavailable | UI shows Shizuku not running; refresh/action buttons do not crash. |
| Shizuku permission missing | UI offers authorization; commands are not treated as successful. |
| Package has no UID | App is omitted from the controllable list. |
| Package name fails validation | Command is rejected and a failed record can persist the error. |
| `cmd netpolicy add` exits non-zero | Record `networkRestricted=false`, `uidPolicyApplied=false`, and the error. |
| `cmd netpolicy remove` exits with `not blacklisted` | Treat as restored and set `networkRestricted=false`. |
| AppOps INTERNET command unsupported | Keep UID policy result, set `delegatedGuardAttempted=true`, `delegatedGuardApplied=false`, and show the unsupported status. |
| User restores an app | Attempt netpolicy removal and AppOps allow if the delegated guard had been applied. |

### 5. Good/Base/Bad Cases

- Good: developer page clearly separates UID policy status from delegated-network guard status.
- Good: Room state records failures so users can see why a package was not restricted.
- Base: devices without AppOps INTERNET support still use UID policy when netpolicy succeeds.
- Bad: claiming this feature fully blocks system-service delegated traffic when only UID netpolicy succeeded.
- Bad: using root-only iptables/nftables commands from Shizuku user service.
- Bad: hard-coding package names for system services to disable globally.

### 6. Tests Required

- GitHub workflow: Android build must compile Room v18 migration, DAO, repository, Shizuku models, and developer UI.
- Manual Shizuku device test: authorize Shizuku, refresh app list, restrict one user app, verify record appears, restore it, verify record status updates.
- Manual unsupported-device test: verify AppOps INTERNET unsupported results remain visible while UID policy status is still accurate.
- Manual safety review: verify Project Lumen's own package is not offered in the controllable app list.

### 7. Wrong vs Correct

#### Wrong

```kotlin
executeShellCommand(userProvidedCommand)
record.networkRestricted = true
```

#### Correct

```kotlin
if (ANDROID_PACKAGE_NAME_REGEX.matches(packageName) && uid > 0) {
    val result = executeShellCommand("cmd netpolicy add restrict-background-blacklist $uid")
    record.networkRestricted = result.success
}
```
