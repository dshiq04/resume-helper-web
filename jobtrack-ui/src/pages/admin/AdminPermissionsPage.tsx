import { useCallback, useEffect, useMemo, useState } from "react";
import {
  App as AntdApp,
  Button,
  Form,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { ReloadOutlined, SafetyCertificateOutlined } from "@ant-design/icons";
import { adminApi } from "../../api/admin";
import type { AdminUser, PermissionItem } from "../../api/types";
import { useI18n } from "../../i18n";
import { errorMessage } from "../../utils/errors";

interface GrantValues {
  userId: number;
  code: string;
}

export function AdminPermissionsPage() {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<GrantValues>();
  const [permissions, setPermissions] = useState<PermissionItem[]>([]);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [granting, setGranting] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    void Promise.all([
      adminApi.permissions(),
      adminApi.permissionUsers(),
    ])
      .then(([permissionList, userPage]) => {
        setPermissions(permissionList);
        setUsers(userPage.items);
      })
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLoading(false));
  }, [message]);

  useEffect(() => {
    load();
  }, [load]);

  const permissionName = useMemo(() => {
    const map = new Map<string, string>();
    permissions.forEach((item) => map.set(item.code, item.name));
    return map;
  }, [permissions]);

  const replaceUser = (updated: AdminUser) => {
    setUsers((list) => list.map((item) => (item.id === updated.id ? updated : item)));
  };

  const grant = async (values: GrantValues) => {
    if (!values.userId) {
      message.warning(t("admin.checkUser"));
      return;
    }
    if (!values.code) {
      message.warning(t("admin.checkPermission"));
      return;
    }
    setGranting(true);
    try {
      const updated = await adminApi.grant(values.userId, values.code);
      replaceUser(updated);
      form.setFieldsValue({ code: undefined });
      message.success(t("admin.granted"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setGranting(false);
    }
  };

  const revoke = async (user: AdminUser, code: string) => {
    try {
      const updated = await adminApi.revoke(user.id, code);
      replaceUser(updated);
      message.success(t("admin.revoked"));
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const columns: ColumnsType<AdminUser> = [
    {
      title: t("admin.username"),
      key: "user",
      width: 220,
      render: (_, record) => (
        <div className="admin-user-link is-static">
          <strong>{record.username}</strong>
          <span>{record.displayName}</span>
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
      title: t("admin.permissionColumn"),
      dataIndex: "permissions",
      render: (codes: string[], record) => (
        <Space wrap size={4}>
          {(codes ?? []).length === 0 && (
            <span className="admin-muted">{t("admin.noPermission")}</span>
          )}
          {(codes ?? []).map((code) => (
            <Popconfirm
              key={code}
              title={t("admin.revokeConfirm")}
              okText={t("common.confirm")}
              cancelText={t("common.cancel")}
              okButtonProps={{ danger: true }}
              onConfirm={() => void revoke(record, code)}
            >
              <Tag
                bordered={false}
                color={code === "ADMIN_CONSOLE" || code === "ADMIN" ? "gold" : "blue"}
                className="admin-permission-tag"
              >
                {permissionName.get(code) || code} · {code}
              </Tag>
            </Popconfirm>
          ))}
        </Space>
      ),
    },
  ];

  const userOptions = useMemo(
    () => users.map((user) => ({
      value: user.id,
      label: `${user.username}${user.displayName ? ` · ${user.displayName}` : ""}`,
      disabled: user.status === "CANCELLED",
    })),
    [users],
  );

  return (
    <div className="admin-permissions-layout">
      <section className="setting-card admin-card">
        <div className="admin-toolbar">
          <div>
            <h2 className="admin-section-title">{t("admin.grantTitle")}</h2>
            <p className="setting-hint">{t("admin.permissionUsersHint")}</p>
          </div>
          <Button icon={<ReloadOutlined />} onClick={load}>{t("admin.refresh")}</Button>
        </div>
        <Form
          form={form}
          layout="inline"
          onFinish={grant}
          requiredMark={false}
          className="admin-grant-form"
        >
          <Form.Item
            name="userId"
            label={t("admin.selectUser")}
            rules={[{ required: true, message: t("admin.checkUser") }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder={t("admin.selectUser")}
              style={{ width: 260 }}
              options={userOptions}
            />
          </Form.Item>
          <Form.Item
            name="code"
            label={t("admin.selectPermission")}
            rules={[{ required: true, message: t("admin.checkPermission") }]}
          >
            <Select
              placeholder={t("admin.selectPermission")}
              style={{ width: 220 }}
              options={permissions.map((item) => ({
                value: item.code,
                label: `${item.name} · ${item.code}`,
              }))}
            />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={granting}
              icon={<SafetyCertificateOutlined />}
            >
              {t("admin.grant")}
            </Button>
          </Form.Item>
        </Form>
      </section>

      <section className="setting-card admin-card">
        <div className="admin-toolbar">
          <h2 className="admin-section-title">{t("admin.userPermissions")}</h2>
        </div>
        <Table<AdminUser>
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={users}
          pagination={{ pageSize: 20, showSizeChanger: false }}
          scroll={{ x: 760 }}
          locale={{ emptyText: t("common.none") }}
        />
      </section>
    </div>
  );
}
