import { useI18n } from "../i18n";
import type { Status } from "../api/types";

const STATUS_CLASS: Record<Status, string> = {
  pending: "is-pending",
  screening: "is-screening",
  written: "is-written",
  interview: "is-interview",
  offer: "is-offer",
  rejected: "is-rejected",
};

export function StatusChip({ status, compact = false }: { status: Status; compact?: boolean }) {
  const { t } = useI18n();
  return (
    <span className={`status-chip ${STATUS_CLASS[status]}${compact ? " is-compact" : ""}`}>
      <i />
      {t(`status.${status}`)}
    </span>
  );
}

export function PriorityChip({ priority }: { priority?: string }) {
  const { t } = useI18n();
  if (!priority) return <span className="muted">—</span>;
  return (
    <span className={`priority-chip priority-${priority}`}>
      {t(`priority.${priority}`) === `priority.${priority}` ? priority : t(`priority.${priority}`)}
    </span>
  );
}
