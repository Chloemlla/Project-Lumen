import type { DashboardData } from "../../types";
import { ColorValue, DataTable, EmptyState, ListRow, Tag } from "../common";
import { MiniChart } from "../MiniChart";

export function TemplateCmsModule({ data }: { data: DashboardData }) {
  if (!data.templates.length) {
    return <EmptyState label="No remote templates have been recorded." />;
  }

  return (
    <DataTable
      headers={["Template", "Tier", "Countdown", "Color", "Locale"]}
      rows={data.templates.map((template) => [
        template.name,
        <Tag text={template.tier} status={template.tier === "FREE" ? "info" : "ok"} />,
        template.style,
        <ColorValue value={template.color} />,
        template.locale || "not recorded",
      ])}
    />
  );
}

export function TemplateEditorModule({ data }: { data: DashboardData }) {
  const template = data.templateEditor;
  const layout = template.layoutJson;
  const titleText = typeof layout.titleText === "string" ? layout.titleText : "";
  const subtitleText = typeof layout.subtitleText === "string" ? layout.subtitleText : "";
  const showSkipButton = typeof layout.showSkipButton === "boolean" ? layout.showSkipButton : true;

  return (
    <div className="split-panel">
      <div className="form-stack">
        <label className="form-row">
          <span>Template name</span>
          <input id="templateNameInput" type="text" defaultValue={template.name} />
        </label>
        <label className="form-row">
          <span>Tier</span>
          <select id="templateTierInput" defaultValue={template.tier || "PRO"}>
            {["PRO", "PLUS", "TEAM", "DEVELOPER", "FREE"].map((tier) => (
              <option key={tier} value={tier}>{tier}</option>
            ))}
          </select>
        </label>
        <label className="form-row">
          <span>Color</span>
          <input id="templateColorInput" type="text" defaultValue={template.color} placeholder="#0f766e" />
        </label>
        <label className="form-row">
          <span>Locales</span>
          <input id="templateLocaleInput" type="text" defaultValue={template.locale || "en, zh"} />
        </label>
        <label className="form-row">
          <span>Rest title</span>
          <input id="templateTitleInput" type="text" defaultValue={titleText} />
        </label>
        <label className="form-row">
          <span>Rest subtitle</span>
          <input id="templateSubtitleInput" type="text" defaultValue={subtitleText} />
        </label>
        <label className="form-row">
          <span>Countdown style</span>
          <select id="templateStyleInput" defaultValue={template.style || "circle"}>
            {["circle", "bar", "number"].map((style) => (
              <option key={style} value={style}>{style}</option>
            ))}
          </select>
        </label>
        <label className="form-row">
          <span>Skip button</span>
          <select id="templateSkipInput" defaultValue={showSkipButton ? "show" : "hide"}>
            <option value="show">show</option>
            <option value="hide">hide</option>
          </select>
        </label>
      </div>
      <pre className="json-view">{JSON.stringify(layout, null, 2)}</pre>
    </div>
  );
}

export function AudioMatrixModule({ data }: { data: DashboardData }) {
  if (!data.audioMatrix.length) {
    return <EmptyState label="No audio or haptic telemetry has been recorded." />;
  }

  return (
    <div className="matrix">
      {data.audioMatrix.map((cell) => (
        <div className="matrix-cell" key={cell.label}>
          <div className="row-title">{cell.label}</div>
          <div className="row-meta">{cell.value} | {cell.meta}</div>
        </div>
      ))}
    </div>
  );
}

export function I18nModule({ data }: { data: DashboardData }) {
  if (!data.i18nJobs.length) {
    return <EmptyState label="No localized template dispatch data has been recorded." />;
  }

  return (
    <div className="list-stack">
      {data.i18nJobs.map((job) => (
        <ListRow
          key={job.locale}
          title={`${job.locale} template pack`}
          meta={`${job.templateCount} templates, ${job.premiumCount} premium | updated ${job.updatedAt}`}
          right={<Tag text={job.status} status={job.status} />}
        />
      ))}
    </div>
  );
}

export function TelemetryModule({ data }: { data: DashboardData }) {
  return (
    <div className="module-stack">
      <MiniChart series={data.telemetry.map((item) => item.value)} tone="info" label="Telemetry aggregate trend" />
      {data.telemetry.length ? (
        <div className="list-stack">
          {data.telemetry.map((item) => {
            const externalSource = item.label.startsWith("External SDK source:");
            const detail = externalSource
              ? `${item.value} calls in last ${item.rangeDays}d`
              : `${item.value}% of anonymous aggregate cohort`;
            return (
              <ListRow
                key={item.label}
                title={item.label}
                meta={detail}
                right={<Tag text={externalSource ? String(item.value) : `${item.value}%`} status="info" />}
              />
            );
          })}
        </div>
      ) : (
        <EmptyState compact label="No anonymous telemetry aggregates have been recorded." />
      )}
    </div>
  );
}
