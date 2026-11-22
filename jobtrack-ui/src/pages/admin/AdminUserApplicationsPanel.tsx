import { useEffect, useState } from "react";
import { App as AntdApp, Descriptions, Input, Select, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { adminApi } from "../../api/admin";
import type { Application, Category, Status } from "../../api/types";
import { useI18n } from "../../i18n";
import { errorMessage } from "../../utils/errors";

const CATEGORIES: Category[] = ["spring", "autumn", "internship", "social"];
const STATUSES: Status[] = [
  "pending",
  "screening",
  "written",
  "interview",
  "offer",
  "rejected",
];

export function AdminUserApplicationsPanel({ userId }: { userId: number }) {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [rows, setRows] = useState<Application[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("all");
  const [status, setStatus] = useState("all");

  const load = (nextPage = page, nextSize = pageSize) => {
    setLoading(true);
    void adminApi
      .applications(userId, {
        search: search || undefined,
        category,
        status,
        page: nextPage,
        pageSize: nextSize,
      })
      .then((data) => {
        setRows(data.items);
        setTotal(data.total);
        setPage(data.page);
        setPageSize(data.pageSize);
      })
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId, search, category, status]);

  const columns: ColumnsType<Application> = [
    {
      title: t("field.company"),
      dataIndex: "companyName",
      width: 200,
      render: (value: string, record) => (
        <div className="admin-app-company">
          <strong>{value}</strong>
          {record.link && (
            <a href={record.link} target="_blank" rel="noreferrer">{record.link}</a>
          )}
        </div>
      ),
    },
    {
      title: t("field.category"),
      dataIndex: "category",
      width: 90,
      render: (value: Category) => <Tag>{t(`category.${value}`)}</Tag>,
    },
    {
      title: t("field.position"),
      dataIndex: "position",
      width: 160,
      render: (value: string | undefined) => value || "—",
    },
    {
      title: t("field.status"),
      dataIndex: "status",
      width: 100,
      render: (value: Status) => <Tag color="blue">{t(`status.${value}`)}</Tag>,
    },
    {
      title: t("field.year"),
      dataIndex: "year",
      width: 80,
    },
    {
      title: t("field.channel"),
      dataIndex: "channel",
      width: 100,
      render: (value: string | undefined) => value || "—",
    },
    {
      title: t("field.city"),
      dataIndex: "city",
      width: 100,
      render: (value: string | undefined) => value || "—",
    },
    {
      title: t("field.appliedAt"),
      dataIndex: "appliedAt",
      width: 130,
      render: (value: string) => (value ? dayjs(value).format("YYYY-MM-DD") : "—"),
    },
  ];

  return (
    <section className="admin-detail-card">
      <div className="admin-toolbar">
        <div className="admin-toolbar-left">
          <Input.Search
            allowClear
            placeholder={t("admin.searchApplications")}
            style={{ width: 220 }}
            onSearch={setSearch}
          />
          <Select
            value={category}
            style={{ width: 120 }}
            onChange={setCategory}
            options={[
              { value: "all", label: t("common.all") },
              ...CATEGORIES.map((item) => ({ value: item, label: t(`category.${item}`) })),
            ]}
          />
          <Select
            value={status}
            style={{ width: 130 }}
            onChange={setStatus}
            options={[
              { value: "all", label: t("common.all") },
              ...STATUSES.map((item) => ({ value: item, label: t(`status.${item}`) })),
            ]}
          />
        </div>
        <span className="admin-muted">{total} {t("common.items")}</span>
      </div>
      <Table<Application>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 980 }}
        expandable={{
          expandedRowRender: (record) => (
            <Descriptions size="small" column={3} bordered>
              <Descriptions.Item label={t("field.salary")}>
                {record.salary || "—"}
              </Descriptions.Item>
              <Descriptions.Item label={t("field.contact")}>
                {record.contact || "—"}
              </Descriptions.Item>
              <Descriptions.Item label={t("field.contactInfo")}>
                {record.contactInfo || "—"}
              </Descriptions.Item>
              <Descriptions.Item label={t("field.priority")}>
                {record.priority ? t(`priority.${record.priority}`) : "—"}
              </Descriptions.Item>
              <Descriptions.Item label={t("field.interviewRound")}>
                {record.interviewRound ?? "—"}
              </Descriptions.Item>
              <Descriptions.Item label={t("field.nextAction")}>
                {record.nextActionAt
                  ? dayjs(record.nextActionAt).format("YYYY-MM-DD HH:mm")
                  : "—"}
              </Descriptions.Item>
              <Descriptions.Item label={t("field.notes")} span={3}>
                <Typography.Paragraph style={{ margin: 0, whiteSpace: "pre-wrap" }}>
                  {record.notes || "—"}
                </Typography.Paragraph>
              </Descriptions.Item>
            </Descriptions>
          ),
        }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50],
          showTotal: (value) => `${value} ${t("common.items")}`,
          onChange: (nextPage, nextSize) => load(nextPage, nextSize),
        }}
        locale={{ emptyText: t("admin.noApplications") }}
      />
    </section>
  );
}
