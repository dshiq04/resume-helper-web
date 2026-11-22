import { useCallback, useEffect, useState } from "react";
import {
  App as AntdApp,
  Avatar,
  Button,
  DatePicker,
  Dropdown,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import type { MenuProps } from "antd";
import { ReloadOutlined, TeamOutlined } from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";
import { useNavigate } from "react-router-dom";
import { adminApi } from "../../api/admin";
import type { AdminUser } from "../../api/types";
import { useI18n } from "../../i18n";
import { errorMessage } from "../../utils/errors";

interface BanValues {
  banType: "PERMANENT" | "TEMPORARY";
  banUntil?: Dayjs;
  reason?: string;
}

function statusColor(status: string) {
  if (status === "BANNED") return "red";
  if (status === "CANCELLED") return "default";
  return "green";
}

export function AdminUsersPage() {
  const { t } = useI18n();
  const { message, modal } = AntdApp.useApp();
  const navigate = useNavigate();
  const [banForm] = Form.useForm<BanValues>();
  const [rows, setRows] = useState<AdminUser[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(true);
  const [banTarget, setBanTarget] = useState<AdminUser | null>(null);
  const [banning, setBanning] = useState(false);
  const banType = Form.useWatch("banType", banForm) ?? "PERMANENT";

  const load = useCallback(
    (targetPage = page) => {
      setLoading(true);
      void adminApi
        .users({ search: search || undefined, status: status || undefined, page: targetPage, pageSize })
        .then((data) => {
          setRows(data.items);
          setTotal(data.total);
          setPage(data.page);
        })
        .catch((error) => message.error(errorMessage(error)))
        .finally(() => setLoading(false));
    },
    [message, page, pageSize, search, status],
  );

  useEffect(() => {
    load(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search, status, pageSize]);

  const replaceRow = (updated: AdminUser) => {
    setRows((list) => list.map((item) => (item.id === updated.id ? updated : item)));
  };

  const changeStatus = (record: AdminUser, next: "NORMAL" | "CANCELLED") => {
    const isCancel = next === "CANCELLED";
    modal.confirm({
      title: isCancel ? t("admin.cancelAccount") : t("admin.restore"),
      content: isCancel ? t("admin.cancelledHint") : t("admin.restoreHint"),
      okText: t("common.confirm"),
      cancelText: t("common.cancel"),
      okButtonProps: { danger: isCancel },
      onOk: async () => {
        try {
          const updated = await adminApi.updateStatus(record.id, { status: next });
          replaceRow(updated);
          message.success(t("admin.statusUpdated"));
        } catch (error) {
          message.error(errorMessage(error));
        }
      },
    });
  };

  const removeUser = (record: AdminUser) => {
    modal.confirm({
      title: t("admin.delete"),
      content: t("admin.deleteConfirm"),
      okText: t("admin.delete"),
      cancelText: t("common.cancel"),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await adminApi.deleteUser(record.id);
          message.success(t("admin.deleted"));
          load(page);
        } catch (error) {
          message.error(errorMessage(error));
        }
      },
    });
  };

  const openBan = (record: AdminUser) => {
    setBanTarget(record);
    banForm.setFieldsValue({
      banType: record.banType === "TEMPORARY" ? "TEMPORARY" : "PERMANENT",
      banUntil: record.banUntil ? dayjs(record.banUntil) : undefined,
      reason: record.banReason ?? "",
    });
  };

  const submitBan = async (values: BanValues) => {
    if (!banTarget) return;
    if (values.banType === "TEMPORARY" && !values.banUntil) {
      message.warning(t("admin.banUntilRequired"));
      return;
    }
    setBanning(true);
    try {
      const updated = await adminApi.updateStatus(banTarget.id, {
        status: "BANNED",
        banType: values.banType,
        banUntil: values.banType === "TEMPORARY"
          ? values.banUntil?.format("YYYY-MM-DDTHH:mm:ss")
          : null,
        reason: values.reason?.trim() || undefined,
      });
      replaceRow(updated);
      setBanTarget(null);
      message.success(t("admin.statusUpdated"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setBanning(false);
    }
  };

  const actionMenu = (record: AdminUser): MenuProps => ({
    items: [
      { key: "detail", label: t("admin.detail") },
      { type: "divider" },
      { key: "normal", label: t("admin.restore"), disabled: record.status === "NORMAL" },
      { key: "ban", label: t("admin.ban") },
      { key: "cancel", label: t("admin.cancelAccount"), disabled: record.status === "CANCELLED" },
      { type: "divider" },
      { key: "delete", label: t("admin.delete"), danger: true },
    ],
    onClick: ({ key }) => {
      if (key === "detail") navigate(`/admin/users/${record.id}`);
      if (key === "normal") changeStatus(record, "NORMAL");
      if (key === "cancel") changeStatus(record, "CANCELLED");
      if (key === "ban") openBan(record);
      if (key === "delete") removeUser(record);
    },
  });

  const columns: ColumnsType<AdminUser> = [
    {
      title: t("admin.username"),
      key: "user",
      width: 250,
      render: (_, record) => (
        <div className="admin-user-cell">
          <Avatar
            size={38}
            src={record.avatarUrl || undefined}
            className="admin-user-avatar"
            onClick={() => navigate(`/admin/users/${record.id}`)}
          >
            {(record.displayName || record.username || "U").slice(0, 1)}
          </Avatar>
          <button
            type="button"
            className="admin-user-link"
            onClick={() => navigate(`/admin/users/${record.id}`)}
          >
            <strong>{record.username}</strong>
            <span>{record.displayName}</span>
          </button>
        </div>
      ),
    },
    {
      title: t("admin.tenant"),
      dataIndex: "tenantName",
      width: 160,
      render: (value: string) => <span className="admin-muted">{value || "—"}</span>,
    },
    {
      title: t("admin.status"),
      dataIndex: "status",
      width: 170,
      render: (_, record) => (
        <Space size={4} wrap>
          <Tag color={statusColor(record.status)}>
            {record.status === "BANNED"
              ? t("admin.statusBanned")
              : record.status === "CANCELLED"
                ? t("admin.statusCancelled")
                : t("admin.statusNormal")}
          </Tag>
          {record.status === "BANNED" && (
            <span className="admin-muted">
              {record.banType === "TEMPORARY" && record.banUntil
                ? `${t("admin.temporary")} · ${dayjs(record.banUntil).format("YYYY-MM-DD HH:mm")}`
                : t("admin.permanent")}
              {record.banReason ? ` · ${record.banReason}` : ""}
            </span>
          )}
        </Space>
      ),
    },
    {
      title: t("admin.permissionsLabel"),
      dataIndex: "permissions",
      width: 190,
      render: (permissions: string[]) => (
        <span className="admin-permission-tags">
          {(permissions ?? []).map((code) => (
            <Tag key={code} bordered={false}>{code}</Tag>
          ))}
        </span>
      ),
    },
    {
      title: t("admin.onlineLabel"),
      dataIndex: "online",
      width: 110,
      render: (online: boolean, record) => (
        <span className={online ? "admin-online is-online" : "admin-muted"}>
          <i className="online-dot" />
          {online ? `${t("admin.onlineLabel")} (${record.onlineSessions})` : t("admin.offline")}
        </span>
      ),
    },
    {
      title: t("admin.lastOperation"),
      dataIndex: "lastOperationAt",
      width: 155,
      render: (value: string | null) => (
        <span className="admin-muted">
          {value ? dayjs(value).format("YYYY-MM-DD HH:mm") : "—"}
        </span>
      ),
    },
    {
      title: t("admin.lastLogin"),
      dataIndex: "lastLoginAt",
      width: 155,
      render: (value: string | null) => (
        <span className="admin-muted">
          {value ? dayjs(value).format("YYYY-MM-DD HH:mm") : "—"}
        </span>
      ),
    },
    {
      title: t("admin.actions"),
      key: "actions",
      width: 110,
      align: "right",
      fixed: "right",
      render: (_, record) => (
        <Dropdown menu={actionMenu(record)} trigger={["click"]}>
          <Button size="small">{t("admin.changeStatus")}</Button>
        </Dropdown>
      ),
    },
  ];

  return (
    <section className="setting-card admin-card">
      <div className="admin-toolbar">
        <div className="admin-toolbar-left">
          <Input.Search
            allowClear
            placeholder={t("admin.searchPlaceholder")}
            style={{ width: 260 }}
            onSearch={(value) => {
              setSearch(value.trim());
              setPage(1);
            }}
          />
          <Select
            value={status}
            style={{ width: 150 }}
            onChange={(value) => {
              setStatus(value);
              setPage(1);
            }}
            options={[
              { value: "", label: t("common.all") },
              { value: "NORMAL", label: t("admin.statusNormal") },
              { value: "CANCELLED", label: t("admin.statusCancelled") },
              { value: "BANNED", label: t("admin.statusBanned") },
            ]}
          />
          <span className="admin-muted admin-total">{total} {t("common.people")}</span>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => load(page)}>
          {t("admin.refresh")}
        </Button>
      </div>

      <Table<AdminUser>
        rowKey="id"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1340 }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50, 100],
          showTotal: (value) => `${value} ${t("common.people")}`,
          onChange: (nextPage, nextSize) => {
            setPage(nextPage);
            setPageSize(nextSize);
            load(nextPage);
          },
        }}
        locale={{
          emptyText: (
            <div className="token-empty">
              <TeamOutlined />
              <strong>{t("common.none")}</strong>
            </div>
          ),
        }}
      />

      <Modal
        open={Boolean(banTarget)}
        title={t("admin.banTitle")}
        confirmLoading={banning}
        okText={t("common.confirm")}
        cancelText={t("common.cancel")}
        onCancel={() => setBanTarget(null)}
        onOk={() => banForm.submit()}
        destroyOnHidden
      >
        <Typography.Paragraph className="admin-muted">
          {banTarget?.username} · {banTarget?.displayName}
        </Typography.Paragraph>
        <Form form={banForm} layout="vertical" onFinish={submitBan} requiredMark={false}>
          <Form.Item name="banType" label={t("admin.banType")} initialValue="PERMANENT">
            <Radio.Group className="admin-ban-types">
              <Radio value="PERMANENT">
                <strong>{t("admin.permanent")}</strong>
                <span>{t("admin.permanentHint")}</span>
              </Radio>
              <Radio value="TEMPORARY">
                <strong>{t("admin.temporary")}</strong>
                <span>{t("admin.temporaryHint")}</span>
              </Radio>
            </Radio.Group>
          </Form.Item>
          {banType === "TEMPORARY" && (
            <Form.Item
              name="banUntil"
              label={t("admin.banUntil")}
              rules={[{ required: true, message: t("admin.banUntilRequired") }]}
            >
              <DatePicker
                showTime
                style={{ width: "100%" }}
                disabledDate={(date) => date.isBefore(dayjs().startOf("day"))}
              />
            </Form.Item>
          )}
          <Form.Item
            name="reason"
            label={t("admin.banReason")}
            className="admin-ban-reason"
          >
            <Input.TextArea
              rows={3}
              maxLength={255}
              showCount
              placeholder={t("admin.banReasonPlaceholder")}
            />
          </Form.Item>
        </Form>
      </Modal>

    </section>
  );
}
