import { statusLabel } from "../../model/dashboardModel";
import type { DashboardData } from "../../types";
import { DataTable, EmptyState, Tag } from "../common";
import { MiniChart } from "../MiniChart";

export function CrashesModule({ data }: { data: DashboardData }) {
  if (!data.crashes.length) {
    return <EmptyState label="No crash groups have been recorded." />;
  }

  return (
    <DataTable
      headers={["Group", "Version", "Count", "Affected", "Risk"]}
      rows={data.crashes.map((crash) => [
        crash.group,
        crash.version,
        crash.count,
        crash.affected,
        <Tag text={statusLabel(crash.risk)} status={crash.risk} />,
      ])}
    />
  );
}

export function StackModule({ data }: { data: DashboardData }) {
  if (!data.stack.length) {
    return <EmptyState label="No sanitized stack frames have been recorded." />;
  }

  return (
    <pre className="stack-view">
      {data.stack.map((line, index) => (
        <span key={`${index}-${line}`} className={stackLineClass(line)}>{line}</span>
      ))}
    </pre>
  );
}

export function VersionAnalysisModule({ data }: { data: DashboardData }) {
  return (
    <div className="module-stack">
      <MiniChart series={data.crashes.map((item) => item.count)} tone="danger" label="Crash count trend" />
      {data.versionAnalysis.length ? (
        <DataTable
          headers={["Version", "Manufacturer", "Crashes", "Affected", "Trend"]}
          rows={data.versionAnalysis.map((item) => [
            item.version,
            item.manufacturer,
            item.crashes,
            item.affected,
            <Tag text={item.trend} status={item.risk} />,
          ])}
        />
      ) : (
        <EmptyState compact label="No version impact telemetry has been recorded." />
      )}
    </div>
  );
}

export function ApiHealthModule({ data }: { data: DashboardData }) {
  const rows = data.apiMetrics.map((metric) => [
    metric.endpoint,
    `${metric.qps}`,
    `${metric.p95Ms} ms`,
    `${metric.status2xx}/${metric.status4xx}/${metric.status5xx}`,
  ]);

  return (
    <div className="module-stack">
      <MiniChart series={data.apiSeries} tone="accent" label="API p95 latency trend" />
      {rows.length ? (
        <DataTable headers={["Endpoint", "QPS", "p95", "2xx/4xx/5xx"]} rows={rows} />
      ) : (
        <EmptyState compact label="No API metric samples have been recorded." />
      )}
    </div>
  );
}

export function SyncThroughputModule({ data }: { data: DashboardData }) {
  const rows = data.syncMetrics.map((metric) => [
    metric.endpoint,
    `${metric.averagePayloadKb} KB`,
    `${metric.largestPayloadKb} KB`,
    `${metric.p95Ms} ms`,
    metric.rejectedPayloads,
  ]);

  return (
    <div className="module-stack">
      <MiniChart series={data.syncSeries} tone="info" label="Sync payload trend" />
      {rows.length ? (
        <DataTable headers={["Endpoint", "Average", "Largest", "p95", "Rejected"]} rows={rows} />
      ) : (
        <EmptyState compact label="No sync throughput samples have been recorded." />
      )}
    </div>
  );
}

function stackLineClass(line: string): string {
  if (line.includes("Exception")) return "stack-line keyword";
  if (line.includes("redacted")) return "stack-line path";
  return "stack-line";
}
