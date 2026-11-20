import { Button } from "antd";
import type { HistoryItem, Status } from "../api/types";
import { useI18n } from "../i18n";
import { formatDate } from "../utils/format";
import { StatusChip } from "./StatusChip";

export function StatusTimeline({
  items,
  current,
  missingStatuses,
  onFill,
}: {
  items: HistoryItem[];
  current: Status;
  missingStatuses: Status[];
  onFill: () => void;
}) {
  const { t } = useI18n();

  return (
    <section className="timeline-panel">
      <div className="panel-title-row">
        <h2>{t("detail.timeline")}</h2>
        {missingStatuses.length > 0 && (
          <Button size="small" onClick={onFill}>
            {t("detail.fill")}
          </Button>
        )}
      </div>
      {missingStatuses.length > 0 && <p className="panel-hint">{t("detail.missing")}</p>}

      {items.length === 0 ? (
        <p className="muted">{t("detail.noHistory")}</p>
      ) : (
        <ol className="timeline">
          {items.map((item, index) => (
            <li
              key={`${item.status}-${item.changedAt}-${index}`}
              className={`timeline-item${item.status === current ? " is-current" : ""}`}
            >
              <span className="timeline-dot" />
              <div className="timeline-content">
                <div className="timeline-top">
                  <StatusChip status={item.status} compact />
                  <span className="timeline-date">{formatDate(item.changedAt, true)}</span>
                </div>
                <div className="timeline-meta">
                  {item.daysSincePrevious !== undefined && (
                    <span>{t("detail.interval", { days: item.daysSincePrevious })}</span>
                  )}
                  {item.daysInStatus !== undefined && (
                    <span>{t("detail.daysInStatus", { days: item.daysInStatus })}</span>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
