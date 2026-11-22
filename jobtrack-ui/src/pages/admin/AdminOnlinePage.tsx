import { useCallback, useEffect, useState } from "react";
import { App as AntdApp, Avatar, Button, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { ReloadOutlined, ThunderboltOutlined, UserOutlined } from "@ant-design/icons";
import { adminApi } from "../../api/admin";
import type { OnlineUser } from "../../api/types";
import { useI18n } from "../../i18n";
import { errorMessage } from "../../utils/errors";

export function AdminOnlinePage() {
  const { t } = useI18n();
  const { message, modal } = AntdApp.useApp();
  const [rows, setRows] = useState<OnlineUser[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    void adminApi
      .online()
      .then(setRows)
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLoading(false));
  }, [message]);

  useEffect(() => {
    load();
    const timer = window.setInterval(load, 30_000);
    return () => window.clearInterval(timer);
  }, [load]);

  const kick = (record: OnlineUser) => {
    modal.confirm({
      title: t("admin.kick"),
      content: t("admin.kickConfirm"),
      okText: t("admin.kick"),
      cancelText: t("common.cancel"),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await adminApi.kick(record.userId);
          message.success(t("admin.kicked"));
          load();
        } catch (error) {
          message.error(errorMessage(error));
        }
      },
    });
  };

  const columns: ColumnsType<OnlineUser> = [
    {
      title: t("admin.username"),
      key: "user",
      render: (_, record) => (
        <div className="admin-user-cell">
          <Avatar size={36} src={record.avatarUrl || undefined} icon={<UserOutlined />} />
          <div className="admin-user-link is-static">
            <strong>{record.username}</strong>
            <span>{record.displayName}</span>
          </div>
        </div>
      ),
    },
    {
      title: t("admin.tenant"),
      dataIndex: "tenantName",
      width: 180,
      render: (value: string) => <span className="admin-muted">{value || "—"}</span>,
    },
    {
      title: t("admin.sessions"),
      dataIndex: "sessions",
      width: 100,
      render: (value: number) => <Tag color="blue">{value}</Tag>,
    },
    {
      title: t("admin.remaining"),
      dataIndex: "remainingSeconds",
      width: 160,
      render: (value: number[]) => {
        const max = Math.max(...(value ?? [0]));
        return (
          <span className="admin-muted">
            {max > 0 ? `${Math.ceil(max / 60)} ${t("admin.minutes")}` : "—"}
          </span>
        );
      },
    },
    {
      title: t("admin.actions"),
      key: "actions",
      width: 100,
      align: "right",
      render: (_, record) => (
        <Button danger size="small" icon={<ThunderboltOutlined />} onClick={() => kick(record)}>
          {t("admin.kick")}
        </Button>
      ),
    },
  ];

  return (
    <section className="setting-card admin-card">
      <div className="admin-toolbar">
        <div>
          <h2 className="admin-section-title">{t("admin.onlineTitle")}</h2>
          <p className="setting-hint">{t("admin.onlineSubtitle")}</p>
        </div>
        <div className="admin-toolbar-right">
          <Tag color="green">{t("admin.onlineCount", { count: rows.length })}</Tag>
          <Button icon={<ReloadOutlined />} onClick={load}>{t("admin.refresh")}</Button>
        </div>
      </div>
      <Table<OnlineUser>
        rowKey="userId"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        scroll={{ x: 760 }}
        locale={{ emptyText: t("admin.noOnline") }}
      />
    </section>
  );
}
