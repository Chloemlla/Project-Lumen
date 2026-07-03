import type { ReactNode } from "react";
import type { AdminSession, DashboardData, ModuleDefinition, RuntimeState } from "../../types";
import { ActionButton, EmptyState, StatusPill } from "../common";
import {
  AudioMatrixModule,
  I18nModule,
  TelemetryModule,
  TemplateCmsModule,
  TemplateEditorModule,
} from "./ContentModules";
import { moduleActions } from "./module-actions";
import {
  ApiHealthModule,
  CrashesModule,
  StackModule,
  SyncThroughputModule,
  VersionAnalysisModule,
} from "./ObservabilityModules";
import {
  AllowlistModule,
  OtaModule,
  RolloutModule,
  RoutesModule,
  SessionSecurityModule,
} from "./ReleaseModules";
import {
  AccessAuditModule,
  BackupsModule,
  DevicesModule,
  PlanModule,
  ProfileModule,
} from "./UserModules";

type ModuleCardProps = {
  module: ModuleDefinition;
  data: DashboardData;
  state: RuntimeState;
  secure: boolean;
  session: AdminSession;
  onAction: (action: string) => void;
  busyAction: string;
};

export function ModuleCard({
  module,
  data,
  state,
  secure,
  session,
  onAction,
  busyAction,
}: ModuleCardProps) {
  return (
    <article className="module-card" data-kind={module.kind}>
      <header className="module-header">
        <div>
          <div className="module-kicker">{module.kicker}</div>
          <h2>{module.title}</h2>
        </div>
        <StatusPill status={module.status} />
      </header>
      <div className="module-body">
        <ModuleBody kind={module.kind} data={data} state={state} secure={secure} session={session} />
      </div>
      <div className="module-actions">
        <div className="inline-actions">
          {moduleActions[module.kind].map((item) => (
            <ActionButton
              key={item.action}
              item={item}
              secure={secure}
              busy={busyAction === item.action}
              onAction={onAction}
            />
          ))}
        </div>
      </div>
    </article>
  );
}

function ModuleBody({ kind, data, state, secure, session }: {
  kind: ModuleDefinition["kind"];
  data: DashboardData;
  state: RuntimeState;
  secure: boolean;
  session: AdminSession;
}): ReactNode {
  switch (kind) {
    case "profile":
      return <ProfileModule data={data} />;
    case "devices":
      return <DevicesModule data={data} />;
    case "accessAudit":
      return <AccessAuditModule data={data} />;
    case "plan":
      return <PlanModule data={data} />;
    case "backups":
      return <BackupsModule data={data} />;
    case "crashes":
      return <CrashesModule data={data} />;
    case "stack":
      return <StackModule data={data} />;
    case "versionAnalysis":
      return <VersionAnalysisModule data={data} />;
    case "apiHealth":
      return <ApiHealthModule data={data} />;
    case "syncThroughput":
      return <SyncThroughputModule data={data} />;
    case "templateCms":
      return <TemplateCmsModule data={data} />;
    case "templateEditor":
      return <TemplateEditorModule data={data} />;
    case "audioMatrix":
      return <AudioMatrixModule data={data} />;
    case "i18n":
      return <I18nModule data={data} />;
    case "telemetry":
      return <TelemetryModule data={data} />;
    case "ota":
      return <OtaModule data={data} />;
    case "rollout":
      return <RolloutModule data={data} />;
    case "routes":
      return <RoutesModule data={data} />;
    case "allowlist":
      return <AllowlistModule data={data} />;
    case "sessionSecurity":
      return <SessionSecurityModule session={session} secure={secure} />;
    default:
      return <EmptyState label={`No renderer is available for ${state.section}.`} />;
  }
}
