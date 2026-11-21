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
  CopyOutlined,
  DeleteOutlined,
  InboxOutlined,
  PlusOutlined,
} from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";
import { accountApi } from "../api/account";
import type { AccessToken, TokenResource, TokenScope } from "../api/types";
import { useI18n } from "../i18n";
import { errorMessage } from "../utils/errors";

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

const ALL_RESOURCES: TokenResource[] = ["applications", "resumes", "statistics", "profile"];

export function AccountTokensPage() {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<CreateValues>();
  const [tokens, setTokens] = useState<AccessToken[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<AccessToken | null>(null);

  useEffect(() => {
    void accountApi
      .tokens()
      .then(setTokens)
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLoading(false));
  }, [message]);

  const scopeLabel = (scope: TokenScope) => {
    if (scope === "read") return t("tokens.scopeRead");
    if (scope === "write") return t("tokens.scopeWrite");
    return t("tokens.scopeReadwrite");
  };

  const resourceLabel = (resource: TokenResource) => {
    if (resource === "applications") return t("tokens.resourceApplications");
    if (resource === "resumes") return t("tokens.resourceResumes");
    if (resource === "statistics") return t("tokens.resourceStatistics");
    return t("tokens.resourceProfile");
  };

  const resourceOptions = ALL_RESOURCES.map((value) => ({
    value,
    label: resourceLabel(value),
  }));

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
      const token = await accountApi.createToken({
        name: values.name?.trim(),
        scope: values.scope,
        resources: values.resources,
        expiresOn: values.permanent ? null : values.expiresOn?.format("YYYY-MM-DD"),
      });
      setTokens((list) => [token, ...list]);
      setCreated(token);
      setCreateOpen(false);
      message.success(t("tokens.created"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setCreating(false);
    }
  };

  const deleteToken = async (id: number) => {
    try {
      await accountApi.deleteToken(id);
      setTokens((list) => list.filter((item) => item.id !== id));
      if (created?.id === id) {
        setCreated(null);
      }
      message.success(t("tokens.deleted"));
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const columns = useMemo<ColumnsType<AccessToken>>(
    () => [
      {
        title: t("tokens.columnName"),
        dataIndex: "name",
        render: (name: string) => <strong className="token-name">{name}</strong>,
      },
      {
        title: t("tokens.columnScope"),
        dataIndex: "scope",
        width: 96,
        render: (scope: TokenScope) => (
          <Tag color={SCOPE_COLORS[scope]}>{scopeLabel(scope)}</Tag>
        ),
      },
      {
        title: t("tokens.columnResources"),
        dataIndex: "resources",
        width: 190,
        render: (resources: TokenResource[]) => (
          <span className="token-resources">
            {(resources ?? []).map((item) => (
              <Tag key={item} bordered={false}>
                {resourceLabel(item)}
              </Tag>
            ))}
          </span>
        ),
      },
      {
        title: t("tokens.columnCode"),
        dataIndex: "code",
        render: (code: string) => (
          <Typography.Text
            code
            copyable={{ text: code, tooltips: [t("tokens.copy"), t("tokens.copied")] }}
          >
            {`${code.slice(0, 7)}••••${code.slice(-4)}`}
          </Typography.Text>
        ),
      },
      {
        title: t("tokens.columnExpires"),
        dataIndex: "expiresOn",
        width: 130,
        render: (expiresOn: string | null) => {
          if (!expiresOn) {
            return <Tag>{t("tokens.permanent")}</Tag>;
          }
          const expired = dayjs(expiresOn).endOf("day").isBefore(dayjs());
          return expired ? (
            <Tag color="red">{t("tokens.expired")}</Tag>
          ) : (
            <span className="token-date">{expiresOn}</span>
          );
        },
      },
      {
        title: t("tokens.columnLastUsed"),
        dataIndex: "lastUsedAt",
        width: 150,
        render: (value: string | null) =>
          value ? (
            <span className="token-date">{dayjs(value).format("YYYY-MM-DD HH:mm")}</span>
          ) : (
            <span className="token-date">—</span>
          ),
      },
      {
        title: t("tokens.columnActions"),
        key: "actions",
        width: 80,
        align: "right",
        render: (_, record) => (
          <Popconfirm
            title={t("tokens.deleteConfirm")}
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
    [created, t],
  );

  return (
    <div className="account-layout">
      <section className="setting-card account-tokens-card">
        <div className="account-tokens-head">
          <div>
            <h2>{t("tokens.title")}</h2>
            <p className="setting-hint">{t("tokens.hint")}</p>
          </div>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t("tokens.generate")}
          </Button>
        </div>

        {created && (
          <div className="token-created">
            <CheckCircleFilled />
            <div>
              <strong>{t("tokens.created")}</strong>
              <p>{t("tokens.createdHint")}</p>
              <Typography.Text
                code
                copyable={{ text: created.code, tooltips: [t("tokens.copy"), t("tokens.copied")] }}
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
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={tokens}
          pagination={false}
          scroll={{ x: 980 }}
          locale={{
            emptyText: (
              <div className="token-empty">
                <InboxOutlined />
                <strong>{t("tokens.empty")}</strong>
                <span>{t("tokens.emptyHint")}</span>
              </div>
            ),
          }}
        />
      </section>

      <section className="setting-card token-api-card">
        <div className="setting-card-head">
          <h2>{t("tokens.validationApi")}</h2>
        </div>
        <p className="setting-hint">{t("tokens.validationApiHint")}</p>
        <div className="token-api-grid">
          <pre>{`POST /api/tokens/validate\nContent-Type: application/json\n\n{ "code": "rt_..." }`}</pre>
          <pre>{`{\n  "valid": true,\n  "scope": "readwrite",\n  "expiresOn": "2026-12-31",\n  "username": "admin",\n  "displayName": "管理员"\n}`}</pre>
        </div>
      </section>

      <Modal
        open={createOpen}
        title={t("tokens.createTitle")}
        confirmLoading={creating}
        okText={t("tokens.generate")}
        cancelText={t("common.cancel")}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={createToken} requiredMark={false}>
          <Form.Item name="name" label={t("tokens.name")}>
            <Input
              maxLength={64}
              placeholder={t("tokens.namePlaceholder")}
              prefix={<CopyOutlined />}
            />
          </Form.Item>
          <Form.Item name="scope" label={t("tokens.scope")} rules={[{ required: true }]}>
            <Segmented
              block
              options={[
                { value: "read", label: t("tokens.scopeRead") },
                { value: "write", label: t("tokens.scopeWrite") },
                { value: "readwrite", label: t("tokens.scopeReadwrite") },
              ]}
            />
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prev, next) => prev.scope !== next.scope}
          >
            {({ getFieldValue }) => {
              const scope = getFieldValue("scope") as TokenScope | undefined;
              const hint =
                scope === "read"
                  ? t("tokens.scopeReadHint")
                  : scope === "write"
                    ? t("tokens.scopeWriteHint")
                    : t("tokens.scopeReadwriteHint");
              return <p className="modal-lead token-scope-hint">{hint}</p>;
            }}
          </Form.Item>
          <Form.Item
            name="resources"
            label={t("tokens.resources")}
            rules={[{ required: true, message: t("tokens.resources") }]}
          >
            <Select mode="multiple" allowClear options={resourceOptions} />
          </Form.Item>
          <Form.Item name="permanent" label={t("tokens.neverExpire")} valuePropName="checked">
            <Switch size="small" />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.permanent !== next.permanent}>
            {({ getFieldValue }) =>
              !getFieldValue("permanent") && (
                <Form.Item
                  name="expiresOn"
                  label={t("tokens.expiresOn")}
                  rules={[{ required: true, message: t("tokens.expiresOn") }]}
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
