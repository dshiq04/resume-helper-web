import { useState } from "react";
import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  LinkOutlined,
  StarFilled,
  StarOutlined,
} from "@ant-design/icons";
import { Input, Popconfirm, Select, Table, Tooltip, type TableColumnsType } from "antd";
import type { Application, Category, Status } from "../api/types";
import { useI18n } from "../i18n";
import { formatDate, openExternalLink } from "../utils/format";
import { StatusChip } from "./StatusChip";

const CATEGORY_KEYS: Record<Category, string> = {
  spring: "category.spring",
  autumn: "category.autumn",
  internship: "category.internship",
  social: "category.social",
};

interface Props {
  items: Application[];
  total: number;
  page: number;
  pageSize: number;
  loading: boolean;
  selectedKeys: number[];
  onSelectionChange: (keys: number[]) => void;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onView: (record: Application) => void;
  onEdit: (record: Application) => void;
  onDelete: (record: Application) => void;
  onPin: (record: Application) => void;
  onStatusChange: (record: Application, status: Status) => void;
  onInlineUpdate: (record: Application, field: "companyName" | "link", value: string) => void;
  statusOptions: { value: Status; label: string }[];
  emptyText: React.ReactNode;
}

export function ApplicationTable(props: Props) {
  const { t } = useI18n();
  const [editing, setEditing] = useState<{
    id: number;
    field: "companyName" | "link";
    value: string;
  } | null>(null);

  const columns: TableColumnsType<Application> = [
    {
      title: t("field.company"),
      dataIndex: "companyName",
      width: 260,
      render: (_, record) => (
        <div className="company-cell">
          <button
            type="button"
            className={`pin-button${record.pinned ? " is-pinned" : ""}`}
            onClick={() => props.onPin(record)}
            title={record.pinned ? t("dash.unpin") : t("dash.pin")}
          >
            {record.pinned ? <StarFilled /> : <StarOutlined />}
          </button>
          <div className="company-main">
            {editing?.id === record.id && editing.field === "companyName" ? (
              <Input
                autoFocus
                size="small"
                value={editing.value}
                onChange={(event) => setEditing({ ...editing, value: event.target.value })}
                onBlur={() => {
                  const value = editing.value.trim();
                  setEditing(null);
                  if (value && value !== record.companyName) {
                    props.onInlineUpdate(record, "companyName", value);
                  }
                }}
                onPressEnter={(event) => event.currentTarget.blur()}
              />
            ) : (
              <button
                type="button"
                className="inline-text"
                onClick={() =>
                  setEditing({ id: record.id, field: "companyName", value: record.companyName })
                }
              >
                {record.companyName}
              </button>
            )}
            <div className="company-sub">
              <span>{record.position || t("common.none")}</span>
              <span className="dot-sep">·</span>
              <span>{record.year}</span>
            </div>
          </div>
          <Tooltip title={t("dash.openLink")}>
            <button
              type="button"
              className="ghost-icon tiny"
              onClick={() => openExternalLink(record.link)}
            >
              <LinkOutlined />
            </button>
          </Tooltip>
        </div>
      ),
    },
    {
      title: t("field.category"),
      dataIndex: "category",
      width: 86,
      render: (value: Category) => <span className="text-chip">{t(CATEGORY_KEYS[value])}</span>,
    },
    {
      title: t("field.status"),
      dataIndex: "status",
      width: 130,
      render: (value: Status, record) => (
        <Select
          size="small"
          variant="borderless"
          value={value}
          className="status-select"
          options={props.statusOptions}
          onChange={(next) => props.onStatusChange(record, next)}
          labelRender={() => <StatusChip status={value} compact />}
        />
      ),
    },
    {
      title: t("field.channel"),
      dataIndex: "channel",
      width: 110,
      render: (value: string, record) => (
        <span className="muted">
          {value || "—"}
          {record.city ? ` · ${record.city}` : ""}
        </span>
      ),
    },
    {
      title: t("field.appliedAt"),
      dataIndex: "appliedAt",
      width: 126,
      render: (value: string, record) => (
        <div className="date-cell">
          <span>{formatDate(value)}</span>
          <em>{record.year}</em>
        </div>
      ),
    },
    {
      title: t("field.statusDays"),
      dataIndex: "statusDays",
      width: 88,
      render: (value: number) => (
        <span className="days-cell">
          {value ?? 0}
          <em>{t("common.days")}</em>
        </span>
      ),
    },
    {
      title: t("common.actions"),
      key: "actions",
      width: 126,
      fixed: "right",
      render: (_, record) => (
        <div className="row-actions">
          <Tooltip title={t("common.view")}>
            <button type="button" className="ghost-icon tiny" onClick={() => props.onView(record)}>
              <EyeOutlined />
            </button>
          </Tooltip>
          <Tooltip title={t("common.edit")}>
            <button type="button" className="ghost-icon tiny" onClick={() => props.onEdit(record)}>
              <EditOutlined />
            </button>
          </Tooltip>
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
      ),
    },
  ];

  return (
    <Table
      rowKey="id"
      size="middle"
      className="ledger-table"
      loading={props.loading}
      columns={columns}
      dataSource={props.items}
      rowClassName={(record) => (record.pinned ? "is-pinned-row" : "")}
      locale={{ emptyText: props.emptyText }}
      rowSelection={{
        selectedRowKeys: props.selectedKeys,
        onChange: (keys) => props.onSelectionChange(keys as number[]),
      }}
      pagination={{
        current: props.page,
        pageSize: props.pageSize,
        total: props.total,
        showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100],
        onChange: (page, size) => {
          if (size !== props.pageSize) {
            props.onPageSizeChange(size);
          } else {
            props.onPageChange(page);
          }
        },
        showTotal: (total) => `共 ${total} ${t("common.items")}`,
      }}
      scroll={{ x: 1000 }}
    />
  );
}
