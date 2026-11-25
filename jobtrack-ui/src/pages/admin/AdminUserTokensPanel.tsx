import { useEffect, useMemo, useState } from "react";
import {
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Switch,
  Table,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  CheckCircleFilled,
  CloseOutlined,
  DeleteOutlined,
  PlusOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";
import { adminApi } from "../../api/admin";
import type {
  AccessToken,
  AccessTokenLog,
  TokenResource,
  TokenScope,
} from "../../api/types";
import { useI18n } from "../../i18n";
import { errorMessage } from "../../utils/errors";

interface CreateValues {
  name?: string;
  scope: TokenScope;
  resources: TokenResource[];
  permanent: boolean;
  expiresOn?: Dayjs;
}

const SCOPE_COLORS: Record<TokenScope, string> = {
  read: "blue",
  write: "orange",
  readwrite: "green",
};

const LOG_COLORS: Record<AccessTokenLog["result"] | string, string> = {
  valid: "green",
  invalid: "red",
  expired: "orange",
  disabled: "default",
  banned: "red",
};

const ALL_RESOURCES: TokenResource[] = ["applications", "resumes", "statistics", "profile"];

export function AdminUserTokensPanel({ userId }: { userId: number }) {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<CreateValues>();
  const [tokens, setTokens] = useState<AccessToken[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<AccessToken | null>(null);
  const [logs, setLogs] = useState<AccessTokenLog[]>([]);
  const [logsLoading, setLogsLoading] = useState(true);
  const [logPage, setLogPage] = useState(1);
  const [logTotal, setLogTotal] = useState(0);
  const [logResult, setLogResult] = useState("");
  const [logName, setLogName] = useState("");

  const loadTokens = () => {
    setLoading(true);
    void adminApi
      .tokens(userId)
      .then(setTokens)
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLoading(false));
  };

  const loadLogs = (page = logPage, name = logName, result = logResult) => {
    setLogsLoading(true);
    void adminApi
      .tokenLogs(userId, {
        page,
        pageSize: 10,
        name: name || undefined,
        result: result || undefined,
      })
      .then((data) => {
        setLogs(data.items);
        setLogTotal(data.total);
        setLogPage(data.page);
      })
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLogsLoading(false));
  };

  useEffect(() => {
    loadTokens();
    loadLogs(1, "", "");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId, message]);

  const scopeLabel = (scope: TokenScope) => {
    if (scope === "read") return t("admin.scopeRead");
    if (scope === "write") return t("admin.scopeWrite");
    return t("admin.scopeReadwrite");
  };

  const resourceLabel = (resource: TokenResource) => {
    if (resource === "applications") return t("admin.resourceApplications");
    if (resource === "resumes") return t("admin.resourceResumes");
    if (resource === "statistics") return t("admin.resourceStatistics");
    return t("admin.resourceProfile");
  };

  const logResultLabel = (result: string) => {
    if (result === "valid") return t("admin.logValid");
    if (result === "expired") return t("admin.logExpired");
    if (result === "disabled") return t("admin.logDisabled");
    if (result === "banned") return t("admin.logBanned");
    return t("admin.logInvalid");
  };

  const openCreate = () => {
    form.resetFields();
    form.setFieldsValue({
      scope: "readwrite",
      resources: ALL_RESOURCES,
      permanent: false,
      expiresOn: dayjs().add(30, "day"),
    });
    setCreateOpen(true);
  };

  const createToken = async (values: CreateValues) => {
    setCreating(true);
    try {
      const token = await adminApi.createToken(userId, {
        name: values.name?.trim(),
        scope: values.scope,
        resources: values.resources,
        expiresOn: values.permanent ? null : values.expiresOn?.format("YYYY-MM-DD"),
      });
      setTokens((list) => [token, ...list]);
      setCreated(token);
      setCreateOpen(false);
      loadLogs(1);
      message.success(t("admin.tokenCreated"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setCreating(false);
    }
  };

  const toggleToken = async (record: AccessToken, enabled: boolean) => {
    try {
      const updated = await adminApi.updateTokenStatus(userId, record.id, enabled);
      setTokens((list) => list.map((item) => (item.id === updated.id ? updated : item)));
      message.success(enabled ? t("admin.tokenEnabled") : t("admin.tokenDisabled"));
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const deleteToken = async (id: number) => {
    try {
      await adminApi.deleteToken(userId, id);
      setTokens((list) => list.filter((item) => item.id !== id));
      if (created?.id === id) {
        setCreated(null);
      }
      message.success(t("admin.tokenDeleted"));
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const columns = useMemo<ColumnsType<AccessToken>>(
    () => [
      {
        title: t("admin.tokenName"),
        dataIndex: "name",
        render: (name: string) => <strong className="token-name">{name}</strong>,
      },
      {
        title: t("admin.tokenScope"),
        dataIndex: "scope",
        width: 90,
        render: (scope: TokenScope) => <Tag color={SCOPE_COLORS[scope]}>{scopeLabel(scope)}</Tag>,
      },
      {
        title: t("admin.tokenStatus"),
        dataIndex: "enabled",
        width: 70,
        render: (enabled: boolean, record) => (
          <Switch
            size="small"
            checked={enabled}
            onChange={(checked) => void toggleToken(record, checked)}
          />
        ),
      },
      {
        title: t("admin.tokenResources"),
        dataIndex: "resources",
        width: 170,
        render: (resources: TokenResource[]) => (
          <span className="token-resources">
            {(resources ?? []).map((item) => (
              <Tag key={item} bordered={false}>{resourceLabel(item)}</Tag>
            ))}
          </span>
        ),
      },
      {
        title: t("admin.tokenCode"),
        dataIndex: "codeMasked",
        render: (masked: string) => <Typography.Text code>{masked || "—"}</Typography.Text>,
      },
      {
        title: t("admin.tokenExpires"),
        dataIndex: "expiresOn",
        width: 120,
        render: (expiresOn: string | null) => {
          if (!expiresOn) return <Tag>{t("admin.permanentToken")}</Tag>;
          const expired = dayjs(expiresOn).endOf("day").isBefore(dayjs());
          return expired
            ? <Tag color="red">{t("admin.expired")}</Tag>
            : <span className="token-date">{expiresOn}</span>;
        },
      },
      {
        title: t("admin.tokenLastUsed"),
        dataIndex: "lastUsedAt",
        width: 140,
        render: (value: string | null) => (
          <span className="token-date">
            {value ? dayjs(value).format("YYYY-MM-DD HH:mm") : "—"}
          </span>
        ),
      },
      {
        title: t("admin.actions"),
        key: "actions",
        width: 70,
        align: "right",
        render: (_, record) => (
          <Popconfirm
            title={t("admin.tokenDeleteConfirm")}
            okText={t("common.confirm")}
            cancelText={t("common.cancel")}
            okButtonProps={{ danger: true }}
            onConfirm={() => void deleteToken(record.id)}
          >
            <Button type="text" danger size="small" icon={<DeleteOutlined />} />
          </Popconfirm>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const logColumns = useMemo<ColumnsType<AccessTokenLog>>(
    () => [
      {
        title: t("admin.logsTime"),
        dataIndex: "createdAt",
        width: 150,
        render: (value: string) =>
          value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "—",
      },
      {
        title: t("admin.tokenCode"),
        dataIndex: "codePrefix",
        render: (_: string, record) => (
          <span className="token-log-code">
            <strong>{record.tokenName || "—"}</strong>
            <span>{record.codePrefix ? `${record.codePrefix}…` : "—"}</span>
          </span>
        ),
      },
      {
        title: t("admin.logsResult"),
        dataIndex: "result",
        width: 90,
        render: (result: string) => (
          <Tag color={LOG_COLORS[result]}>{logResultLabel(result)}</Tag>
        ),
      },
      {
        title: t("admin.tokenScope"),
        dataIndex: "scope",
        width: 90,
        render: (scope: TokenScope | null) => (scope ? scopeLabel(scope) : "—"),
      },
      {
        title: t("admin.tokenResources"),
        dataIndex: "resources",
        width: 170,
        render: (resources: TokenResource[]) => (
          <span className="token-resources">
            {(resources ?? []).map((item) => (
              <Tag key={item} bordered={false}>{resourceLabel(item)}</Tag>
            ))}
          </span>
        ),
      },
      {
        title: t("admin.logsIp"),
        dataIndex: "ip",
        width: 120,
        render: (ip: string | null) => <span className="token-date">{ip || "—"}</span>,
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <div className="admin-token-panel">
      <section className="admin-detail-card">
        <div className="admin-tokens-head">
          <div>
            <h2>{t("admin.tokensTitle")}</h2>
            <p className="setting-hint">{t("admin.tokensHint")}</p>
          </div>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t("admin.generateToken")}
          </Button>
        </div>
        {created && (
          <div className="token-created">
            <CheckCircleFilled />
            <div>
              <strong>{t("admin.tokenCreated")}</strong>
              <p>{t("tokens.createdHint")}</p>
              <Typography.Text
                code
                copyable={{
                  text: created.code,
                  tooltips: [t("admin.copy"), t("admin.copied")],
                }}
                className="token-code"
              >
                {created.code}
              </Typography.Text>
            </div>
            <Button
              type="text"
              size="small"
              icon={<CloseOutlined />}
              onClick={() => setCreated(null)}
            />
          </div>
        )}
        <Table<AccessToken>
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={tokens}
          pagination={false}
          scroll={{ x: 960 }}
          locale={{ emptyText: t("admin.noTokens") }}
        />
      </section>

      <section className="admin-detail-card">
        <div className="admin-tokens-head">
          <div>
            <h2>{t("admin.tokenLogsTitle")}</h2>
            <p className="setting-hint">{t("admin.tokenLogsHint")}</p>
          </div>
          <div className="token-log-actions">
            <Input.Search
              allowClear
              placeholder={t("admin.logNameFilter")}
              style={{ width: 160 }}
              onSearch={(value) => {
                setLogName(value);
                loadLogs(1, value);
              }}
            />
            <Select
              value={logResult}
              style={{ width: 130 }}
              onChange={(value) => {
                setLogResult(value);
                loadLogs(1, logName, value);
              }}
              options={[
                { value: "", label: t("admin.allResults") },
                { value: "valid", label: t("admin.logValid") },
                { value: "invalid", label: t("admin.logInvalid") },
                { value: "expired", label: t("admin.logExpired") },
                { value: "disabled", label: t("admin.logDisabled") },
                { value: "banned", label: t("admin.logBanned") },
              ]}
            />
            <Button icon={<ReloadOutlined />} onClick={() => loadLogs(logPage)}>
              {t("admin.refresh")}
            </Button>
          </div>
        </div>
        <Table<AccessTokenLog>
          rowKey="id"
          size="small"
          loading={logsLoading}
          columns={logColumns}
          dataSource={logs}
          pagination={{
            current: logPage,
            pageSize: 10,
            total: logTotal,
            showSizeChanger: false,
            onChange: (page) => loadLogs(page),
          }}
          scroll={{ x: 840 }}
          locale={{ emptyText: t("admin.noLogs") }}
        />
      </section>

      <Modal
        open={createOpen}
        title={t("admin.createTokenTitle")}
        confirmLoading={creating}
        okText={t("admin.generateToken")}
        cancelText={t("common.cancel")}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={createToken} requiredMark={false}>
          <Form.Item name="name" label={t("admin.tokenName")}>
            <Input maxLength={64} placeholder={t("admin.tokenNamePlaceholder")} />
          </Form.Item>
          <Form.Item name="scope" label={t("admin.tokenScope")} rules={[{ required: true }]}>
            <Segmented
              block
              options={[
                { value: "read", label: t("admin.scopeRead") },
                { value: "write", label: t("admin.scopeWrite") },
                { value: "readwrite", label: t("admin.scopeReadwrite") },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="resources"
            label={t("admin.tokenResources")}
            rules={[{ required: true, message: t("admin.tokenResources") }]}
          >
            <Select
              mode="multiple"
              allowClear
              options={ALL_RESOURCES.map((value) => ({ value, label: resourceLabel(value) }))}
            />
          </Form.Item>
          <Form.Item name="permanent" label={t("admin.neverExpire")} valuePropName="checked">
            <Switch size="small" />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.permanent !== next.permanent}>
            {({ getFieldValue }) =>
              !getFieldValue("permanent") && (
                <Form.Item
                  name="expiresOn"
                  label={t("admin.expiresOn")}
                  rules={[{ required: true, message: t("admin.expiresOn") }]}
                >
                  <DatePicker
                    style={{ width: "100%" }}
                    disabledDate={(date) => date.isBefore(dayjs().startOf("day"))}
                  />
                </Form.Item>
              )
            }
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
