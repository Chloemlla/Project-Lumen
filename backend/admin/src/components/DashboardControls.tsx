import { Gauge, Search, SlidersHorizontal } from "lucide-react";
import type { AdminSection } from "../types";
import { StatusPill } from "./common";

type DashboardControlsProps = {
  query: string;
  environment: string;
  range: string;
  selectedSection: AdminSection;
  onQuery: (value: string) => void;
  onEnvironment: (value: string) => void;
  onRange: (value: string) => void;
};

export function DashboardControls({
  query,
  environment,
  range,
  selectedSection,
  onQuery,
  onEnvironment,
  onRange,
}: DashboardControlsProps) {
  return (
    <>
      <section className="controls-strip" aria-label="Dashboard controls">
        <label className="search-field">
          <span>Search modules</span>
          <div className="input-with-icon">
            <Search size={16} aria-hidden="true" />
            <input
              type="search"
              placeholder="Search users, crashes, templates, releases"
              value={query}
              onChange={(event) => onQuery(event.currentTarget.value)}
            />
          </div>
        </label>
        <label className="select-field">
          <span>Environment</span>
          <div className="input-with-icon">
            <SlidersHorizontal size={16} aria-hidden="true" />
            <select value={environment} onChange={(event) => onEnvironment(event.currentTarget.value)}>
              <option value="production">Production</option>
              <option value="staging">Staging</option>
              <option value="local">Local</option>
            </select>
          </div>
        </label>
        <label className="select-field">
          <span>Range</span>
          <div className="input-with-icon">
            <Gauge size={16} aria-hidden="true" />
            <select value={range} onChange={(event) => onRange(event.currentTarget.value)}>
              <option value="7">Last 7 days</option>
              <option value="30">Last 30 days</option>
              <option value="90">Last 90 days</option>
            </select>
          </div>
        </label>
      </section>

      <section className="section-strip" aria-label="Current section modules">
        {selectedSection.modules.map((module) => (
          <button
            key={module.kind}
            className="section-chip"
            type="button"
            onClick={() => onQuery(module.title)}
          >
            <span>{module.kicker}</span>
            <StatusPill status={module.status} />
          </button>
        ))}
      </section>
    </>
  );
}
