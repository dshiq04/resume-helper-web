import {
  DeleteOutlined,
  EditOutlined,
  LinkOutlined,
  StarFilled,
  StarOutlined,
} from "@ant-design/icons";
import { Checkbox, Popconfirm, Select } from "antd";
import type { Application, Status } from "../api/types";
import { useI18n } from "../i18n";
import { formatDate, openExternalLink } from "../utils/format";
import { StatusChip } from "./StatusChip";

interface Props {
  items: Application[];
  loading: boolean;
  selectedKeys: number[];
  onToggleSelect: (id: number) => void;
  onView: (record: Application) => void;
  onEdit: (record: Application) => void;
  onDelete: (record: Application) => void;
  onPin: (record: Application) => void;
  onStatusChange: (record: Application, status: Status) => void;
  statusOptions: { value: Status; label: string }[];
  emptyText: React.ReactNode;
}

export function ApplicationCards(props: Props) {
  const { t } = useI18n();

  if (!props.loading && props.items.length === 0) {
    return <div className="card-empty">{props.emptyText}</div>;
  }

  return (
    <div className="application-cards">
      {props.items.map((record) => (
        <article
          key={record.id}
          className={`application-card${record.pinned ? " is-pinned" : ""}`}
        >
          <div className="application-card-top">
            <Checkbox
              checked={props.selectedKeys.includes(record.id)}
              onChange={() => props.onToggleSelect(record.id)}
            />
            <button
              type="button"
              className={`pin-button${record.pinned ? " is-pinned" : ""}`}
              onClick={() => props.onPin(record)}
            >
              {record.pinned ? <StarFilled /> : <StarOutlined />}
            </button>
            <div className="application-card-company" onClick={() => props.onView(record)}>
              <strong>{record.companyName}</strong>
              <span>
                {record.position || t("common.none")} · {record.year}
              </span>
            </div>
            <button
              type="button"
              className="ghost-icon tiny"
              onClick={() => openExternalLink(record.link)}
            >
              <LinkOutlined />
            </button>
          </div>

          <div className="application-card-meta">
            <StatusChip status={record.status} compact />
            <span className="muted">
              {record.channel || "—"}
              {record.city ? ` · ${record.city}` : ""}
            </span>
            <span className="muted">{formatDate(record.appliedAt)}</span>
            {(record.statusDays ?? 0) > 0 && (
              <span className="muted">
                {record.statusDays} {t("common.days")}
              </span>
            )}
          </div>

          <div className="application-card-actions">
            <Select
              size="small"
              value={record.status}
              options={props.statusOptions}
              onChange={(next) => props.onStatusChange(record, next)}
              labelRender={() => <StatusChip status={record.status} compact />}
              className="status-select"
            />
            <div className="row-actions">
              <button type="button" className="ghost-icon tiny" onClick={() => props.onEdit(record)}>
                <EditOutlined />
              </button>
              <Popconfirm
                title={t("dash.deleteConfirm", { name: record.companyName })}
                onConfirm={() => props.onDelete(record)}
                okText={t("common.confirm")}
                cancelText={t("common.cancel")}
              >
                <button type="button" className="ghost-icon tiny danger">
                  <DeleteOutlined />
                </button>
              </Popconfirm>
            </div>
          </div>
        </article>
      ))}
    </div>
  );
}
