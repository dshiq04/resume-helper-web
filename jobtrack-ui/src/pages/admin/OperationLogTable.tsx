import { useEffect, useMemo, useState } from "react";
import {
  App as AntdApp,
  Button,
  DatePicker,
  Input,
  Select,
  Space,
  Table,
  Tag,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { ReloadOutlined } from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";
import { adminApi } from "../../api/admin";
import type { OperationType, UserOperationLog } from "../../api/types";
import { useI18n } from "../../i18n";
import { errorMessage } from "../../utils/errors";

const OPERATION_TYPES: OperationType[] = [
  "LOGIN",
  "LOGOUT",
  "REGISTER",
  "AGREEMENT",
  "PROFILE",
  "SECURITY",
  "APPLICATION",
  "RESUME",
  "TOKEN",
  "ACCOUNT",
  "ADMIN",
  "OTHER",
];

const TYPE_COLORS: Record<string, string> = {
  LOGIN: "green",
  LOGOUT: "default",
  REGISTER: "cyan",
  AGREEMENT: "purple",
  PROFILE: "geekblue",
  SECURITY: "orange",
  APPLICATION: "blue",
  RESUME: "magenta",
  TOKEN: "gold",
  ACCOUNT: "red",
  ADMIN: "volcano",
  OTHER: "default",
};

export function OperationLogTable({
  userId,
  embedded = false,
}: {
  userId?: number;
  embedded?: boolean;
}) {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [rows, setRows] = useState<UserOperationLog[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(embedded ? 10 : 20);
  const [type, setType] = useState("ALL");
  const [keyword, setKeyword] = useState("");
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    const from = range?.[0]?.startOf("day").format("YYYY-MM-DDTHH:mm:ss");
    const to = range?.[1]?.endOf("day").format("YYYY-MM-DDTHH:mm:ss");
    void adminApi
      .operations({
        userId,
        type,
        from,
        to,
        keyword: keyword || undefined,
        page,
        pageSize,
      })
      .then((data) => {
        setRows(data.items);
        setTotal(data.total);
        setPage(data.page);
        setPageSize(data.pageSize);
      })
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLoading(false));
  }, [userId, type, keyword, range, page, pageSize, message]);

  const refresh = () => {
    // 通过改变页码外的空刷新不易触发，这里直接重新请求当前页。
    setLoading(true);
    const from = range?.[0]?.startOf("day").format("YYYY-MM-DDTHH:mm:ss");
    const to = range?.[1]?.endOf("day").format("YYYY-MM-DDTHH:mm:ss");
    void adminApi
      .operations({ userId, type, from, to, keyword: keyword || undefined, page, pageSize })
      .then((data) => {
        setRows(data.items);
        setTotal(data.total);
        setPage(data.page);
        setPageSize(data.pageSize);
      })
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLoading(false));
  };

  const typeOptions = useMemo(
    () => [
      { value: "ALL", label: t("common.all") },
      ...OPERATION_TYPES.map((value) => ({
        value,
        label: t(`admin.opType.${value}`),
      })),
    ],
    [t],
  );

  const columns: ColumnsType<UserOperationLog> = [
    {
      title: t("admin.opTime"),
      dataIndex: "createdAt",
      width: 160,
      render: (value: string) => (
        <span className="admin-op-time">{dayjs(value).format("YYYY-MM-DD HH:mm:ss")}</span>
      ),
    },
    ...(!userId
      ? [{
          title: t("admin.opUser"),
          key: "user",
          width: 180,
          render: (_: unknown, record: UserOperationLog) => (
            <div className="admin-operation-user">
              <strong>{record.username || `#${record.userId}`}</strong>
              <span>{record.displayName || "—"}</span>
            </div>
          ),
        } as ColumnsType<UserOperationLog>[number]]
      : []),
    {
      title: t("admin.opTypeLabel"),
      dataIndex: "operationType",
      width: 120,
      render: (value: string) => (
        <Tag color={TYPE_COLORS[value] ?? "default"}>
          {t(`admin.opType.${value}`) === `admin.opType.${value}`
            ? value
            : t(`admin.opType.${value}`)}
        </Tag>
      ),
    },
    {
      title: t("admin.opDescription"),
      dataIndex: "description",
      render: (value: string, record) => (
        <div className="admin-operation-desc">
          <strong>{value || "—"}</strong>
          {record.path && (
            <span>{record.method ? `${record.method} ` : ""}{record.path}</span>
          )}
        </div>
      ),
    },
    {
      title: t("admin.opIp"),
      dataIndex: "ip",
      width: 140,
      render: (value: string) => (
        <span className="admin-muted">{value || "—"}</span>
      ),
    },
  ];

  return (
    <section className={embedded ? "admin-card admin-operation-embedded" : "setting-card admin-card"}>
      <div className="admin-toolbar">
        <div className="admin-toolbar-left">
          {!userId && (
            <Input.Search
              allowClear
              placeholder={t("admin.opSearch")}
              style={{ width: 220 }}
              onSearch={(value) => {
                setKeyword(value.trim());
                setPage(1);
              }}
            />
          )}
          <Select
            value={type}
            style={{ width: 140 }}
            options={typeOptions}
            onChange={(value) => {
              setType(value);
              setPage(1);
            }}
          />
          <DatePicker.RangePicker
            value={range}
            onChange={(values) => {
              setRange(values as [Dayjs, Dayjs] | null);
              setPage(1);
            }}
            placeholder={[t("admin.opFrom"), t("admin.opTo")]}
          />
        </div>
        <Space wrap>
          <span className="admin-muted">{total} {t("common.items")}</span>
          <Button icon={<ReloadOutlined />} onClick={refresh}>
            {t("admin.refresh")}
          </Button>
        </Space>
      </div>
      <Table<UserOperationLog>
        rowKey="id"
        size={embedded ? "small" : "middle"}
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: userId ? 720 : 940 }}
        locale={{ emptyText: t("admin.opEmpty") }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50, 100],
          onChange: (nextPage, nextSize) => {
            setPage(nextPage);
            setPageSize(nextSize);
          },
        }}
      />
    </section>
  );
}
