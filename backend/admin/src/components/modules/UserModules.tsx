import { backupSummary, shortFingerprint } from "../../model/dashboardModel";
import type { DashboardData } from "../../types";
import { DataTable, EmptyState, ListRow, Tag, TimelineItem } from "../common";

export function ProfileModule({ data }: { data: DashboardData }) {
  return (
    <div className="module-stack">
      <DataTable
        headers={["Field", "Value"]}
        rows={[
          ["Email", data.profile.email],
          ["Registered", data.profile.registeredAt],
          ["Last sync", data.profile.lastSyncAt],
          ["Plan tier", <Tag text={data.profile.planTier} status="ok" />],
          ["Local security", data.profile.localSecurity],
        ]}
      />
      {data.profile.featureFlags.length ? (
        <div className="list-stack">
          {data.profile.featureFlags.map((flag) => (
            <ListRow
              key={flag}
              title={flag}
              meta="Feature flag enabled"
              right={<Tag text="enabled" status="ok" />}
            />
          ))}
        </div>
      ) : (
        <EmptyState compact label="No feature flags are enabled for the selected user." />
      )}
    </div>
  );
}

export function DevicesModule({ data }: { data: DashboardData }) {
  if (!data.devices.length) {
    return <EmptyState label="No registered device assets have been recorded." />;
  }

  return (
    <div className="list-stack">
      {data.devices.map((device) => (
        <ListRow
          key={device.id}
          title={device.model}
          meta={`${device.id} | fingerprint ${shortFingerprint(device.fingerprint)} | versionCode ${device.versionCode} | ${device.config}`}
          right={<Tag text={device.lastSeen} status="info" />}
        />
      ))}
    </div>
  );
}

export function AccessAuditModule({ data }: { data: DashboardData }) {
  if (!data.accessAudit.length) {
    return <EmptyState label="No admin access audit entries have been recorded." />;
  }

  return (
    <DataTable
      headers={["Time", "Endpoint", "IP", "Geo", "Status"]}
      rows={data.accessAudit.map((entry) => [
        entry.time,
        <code>{entry.endpoint}</code>,
        entry.ip,
        entry.geo,
        <Tag text={String(entry.status)} status={entry.status === 200 ? "ok" : "risk"} />,
      ])}
    />
  );
}

export function PlanModule({ data }: { data: DashboardData }) {
  const users = data.users.length
    ? data.users
    : (data.profile.id
      ? [{ id: data.profile.id, email: data.profile.email, planTier: data.profile.planTier, lastSyncAt: data.profile.lastSyncAt }]
      : []);
  const entitlementRows = data.entitlements.map((entry) => [
    <code>{entry.userId}</code>,
    entry.product,
    <Tag text={entry.tier} status={entry.status === "active" ? "ok" : "watch"} />,
    <Tag text={entry.status} status={entry.status === "active" ? "ok" : "risk"} />,
    entry.expiresAt,
  ]);

  return (
    <div className="split-panel">
      <div className="form-stack">
        {users.length ? null : <EmptyState compact label="No users are available for entitlement changes." />}
        <label className="form-row">
          <span>User</span>
          <select id="planUserIdInput" disabled={!users.length}>
            {users.map((user) => (
              <option key={user.id} value={user.id}>{user.email} | {user.planTier || "FREE"}</option>
            ))}
          </select>
        </label>
        <label className="form-row">
          <span>Plan tier</span>
          <select id="planTierInput" defaultValue="PRO">
            {["PRO", "PLUS", "TEAM", "DEVELOPER", "FREE"].map((tier) => (
              <option key={tier} value={tier}>{tier}</option>
            ))}
          </select>
        </label>
        <label className="form-row">
          <span>Product ID</span>
          <input id="planProductIdInput" type="text" defaultValue="manual_admin_pro" />
        </label>
        <label className="form-row">
          <span>Expires at</span>
          <input id="planExpiresAtInput" type="number" min="0" defaultValue="0" />
        </label>
      </div>
      <div className="module-stack">
        {entitlementRows.length ? (
          <DataTable headers={["User", "Product", "Tier", "Status", "Expires"]} rows={entitlementRows} />
        ) : (
          <EmptyState compact label="No entitlement records have been recorded." />
        )}
        {data.purchaseAudit.length ? (
          <div className="timeline">
            {data.purchaseAudit.map((entry) => (
              <TimelineItem
                key={`${entry.time}-${entry.product}-${entry.action}`}
                time={entry.time}
                title={entry.product}
                meta={`${entry.status} | ${entry.token} | ${entry.action}`}
              />
            ))}
          </div>
        ) : null}
      </div>
    </div>
  );
}

export function BackupsModule({ data }: { data: DashboardData }) {
  if (!data.backups.length) {
    return <EmptyState label="No cloud backups have been uploaded yet." />;
  }

  const latest = data.backups[0];
  if (!latest) {
    return <EmptyState label="No cloud backups have been uploaded yet." />;
  }

  return (
    <div className="split-panel">
      <div className="timeline">
        {data.backups.map((backup) => (
          <TimelineItem
            key={backup.id}
            time={backup.uploadedAt}
            title={backup.id}
            meta={backupSummary(backup.summary)}
          />
        ))}
      </div>
      <pre className="json-view">{JSON.stringify(latest.summary, null, 2)}</pre>
    </div>
  );
}
