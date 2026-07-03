import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { normalizeStatus, safeColor, statusLabel } from "../model/dashboardModel";

export type ActionButtonConfig = {
  label: string;
  action: string;
  sensitive?: boolean;
  variant?: "primary" | "secondary" | "danger";
  icon: LucideIcon;
};

type ButtonProps = {
  item: ActionButtonConfig;
  secure: boolean;
  busy: boolean;
  onAction: (action: string) => void;
};

export function ActionButton({ item, secure, busy, onAction }: ButtonProps) {
  const Icon = item.icon;
  const disabled = busy || Boolean(item.sensitive && !secure);
  const title = item.sensitive && !secure ? "Requires HTTPS or localhost" : item.label;

  return (
    <button
      className={`button ${item.variant || "secondary"} button-with-icon`}
      type="button"
      disabled={disabled}
      title={title}
      aria-label={title}
      onClick={() => onAction(item.action)}
    >
      <Icon size={15} strokeWidth={2.1} aria-hidden="true" />
      <span>{busy ? "Working" : item.label}</span>
    </button>
  );
}

export function StatusPill({ status }: { status: string }) {
  const tone = normalizeStatus(status);
  return <span className={`status-pill status-${tone}`}>{statusLabel(status)}</span>;
}

export function Tag({ text, status = "info" }: { text: ReactNode; status?: string }) {
  const tone = normalizeStatus(status);
  return <span className={`tag tag-${tone}`}>{text}</span>;
}

export function DataTable({ headers, rows }: { headers: string[]; rows: ReactNode[][] }) {
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            {headers.map((header) => <th key={header}>{header}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {row.map((item, cellIndex) => <td key={cellIndex}>{item}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function EmptyState({ label, compact = false, wide = false }: {
  label: string;
  compact?: boolean;
  wide?: boolean;
}) {
  return <div className={`empty-state${compact ? " compact" : ""}${wide ? " wide" : ""}`}>{label}</div>;
}

export function ListRow({ title, meta, right }: {
  title: ReactNode;
  meta: ReactNode;
  right?: ReactNode;
}) {
  return (
    <div className="list-row">
      <div>
        <div className="row-title">{title}</div>
        <div className="row-meta">{meta}</div>
      </div>
      {right ? <div className="row-right">{right}</div> : null}
    </div>
  );
}

export function TimelineItem({ time, title, meta }: {
  time: string;
  title: ReactNode;
  meta: ReactNode;
}) {
  return (
    <div className="timeline-item">
      <div className="row-title">{title}</div>
      <div className="row-meta">{time} | {meta}</div>
    </div>
  );
}

export function ColorValue({ value }: { value: string }) {
  const color = safeColor(value);
  return (
    <span className="color-value">
      <span className="color-swatch" style={color ? { background: color } : undefined} />
      <span>{value || "not recorded"}</span>
    </span>
  );
}
