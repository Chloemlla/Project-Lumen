import { statusLabel, tokenExpiryLabel } from "../../model/dashboardModel";
import type { AdminSession, DashboardData } from "../../types";
import { DataTable, EmptyState, ListRow, Tag } from "../common";

export function OtaModule({ data }: { data: DashboardData }) {
  if (!data.releases.length) {
    return <EmptyState label="No release integrity records have been recorded." />;
  }

  return (
    <DataTable
      headers={["versionCode", "Release", "SHA256", "Rollout", "Policy"]}
      rows={data.releases.map((release) => [
        release.version,
        release.name,
        <code>{release.sha || "not recorded"}</code>,
        release.rollout,
        <Tag text={release.force ? "force update" : "normal"} status={release.force ? "risk" : "ok"} />,
      ])}
    />
  );
}

export function RolloutModule({ data }: { data: DashboardData }) {
  if (!data.rolloutPlan.length) {
    return <EmptyState label="No release rollout policy has been recorded." />;
  }

  return (
    <div className="list-stack">
      {data.rolloutPlan.map((item) => (
        <ListRow
          key={`${item.title}-${item.detail}`}
          title={item.title}
          meta={item.detail}
          right={<Tag text={statusLabel(item.status)} status={item.status} />}
        />
      ))}
    </div>
  );
}

export function RoutesModule({ data }: { data: DashboardData }) {
  if (!data.routes.length) {
    return <EmptyState label="No route health samples have been recorded." />;
  }

  return (
    <DataTable
      headers={["Module", "Path", "State", "p95"]}
      rows={data.routes.map((route) => [
        route.module,
        <code>{route.path}</code>,
        <Tag text={statusLabel(route.status)} status={route.status} />,
        route.p95,
      ])}
    />
  );
}

export function AllowlistModule({ data }: { data: DashboardData }) {
  const first = data.allowlist[0];

  return (
    <div className="split-panel">
      <div className="form-stack">
        <label className="form-row">
          <span>Origin</span>
          <input id="allowlistOriginInput" type="text" defaultValue={first?.origin || ""} placeholder="admin.example.com" />
        </label>
        <label className="form-row">
          <span>Protocol</span>
          <select id="allowlistProtocolInput" defaultValue={first?.protocol || "https"}>
            <option value="https">https</option>
            <option value="http">http</option>
          </select>
        </label>
        <label className="form-row">
          <span>Risk</span>
          <select id="allowlistRiskInput" defaultValue={first?.risk || "required"}>
            <option value="required">required</option>
            <option value="review">review</option>
            <option value="blocked">blocked</option>
          </select>
        </label>
      </div>
      {data.allowlist.length ? (
        <DataTable
          headers={["Origin", "Protocol", "Risk"]}
          rows={data.allowlist.map((entry) => [
            entry.origin,
            <Tag text={entry.protocol} status={entry.protocol === "https" ? "ok" : "risk"} />,
            entry.risk,
          ])}
        />
      ) : (
        <EmptyState compact label="No admin origin allowlist records have been recorded." />
      )}
    </div>
  );
}

export function SessionSecurityModule({ session, secure }: {
  session: AdminSession;
  secure: boolean;
}) {
  return (
    <DataTable
      headers={["Field", "Value"]}
      rows={[
        ["Transport", <Tag text={secure ? "secure" : "locked"} status={secure ? "ok" : "risk"} />],
        ["Token in memory", <Tag text={session.token ? "yes" : "no"} status={session.token ? "ok" : "watch"} />],
        ["Token expiry", tokenExpiryLabel(session.tokenExpiresAt)],
        ["Session storage", "admin tokens are not written to localStorage"],
      ]}
    />
  );
}
