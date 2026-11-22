import { useEffect, useState } from "react";
import { App as AntdApp, Button, Input, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { DownloadOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { adminApi } from "../../api/admin";
import { downloadBlob } from "../../api/client";
import type { Resume } from "../../api/types";
import { useI18n } from "../../i18n";
import { errorMessage } from "../../utils/errors";

function fileSize(value: number) {
  if (!value) return "0 B";
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(2)} MB`;
}

export function AdminUserResumesPanel({ userId }: { userId: number }) {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [rows, setRows] = useState<Resume[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [downloading, setDownloading] = useState<number | null>(null);

  const load = (keyword = search) => {
    setLoading(true);
    void adminApi
      .resumes(userId, keyword || undefined)
      .then((data) => setRows(data.items))
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load("");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

  const download = async (record: Resume) => {
    setDownloading(record.id);
    try {
      const blob = await adminApi.resumeFile(userId, record.id);
      downloadBlob(blob, record.fileName || record.name);
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setDownloading(null);
    }
  };

  const columns: ColumnsType<Resume> = [
    {
      title: t("field.resume"),
      dataIndex: "name",
      render: (value: string, record) => (
        <div className="admin-resume-name">
          <strong>{value}</strong>
          <span>{record.fileName}</span>
        </div>
      ),
    },
    {
      title: t("field.tags"),
      dataIndex: "tags",
      width: 220,
      render: (tags: string[]) => (
        <span className="admin-permission-tags">
          {(tags ?? []).map((tag) => (
            <Tag key={tag} bordered={false}>{tag}</Tag>
          ))}
        </span>
      ),
    },
    {
      title: t("admin.fileType"),
      dataIndex: "fileType",
      width: 90,
      render: (value: string) => <Tag>{value?.toUpperCase()}</Tag>,
    },
    {
      title: t("admin.fileSize"),
      dataIndex: "fileSize",
      width: 100,
      render: (value: number) => <span className="admin-muted">{fileSize(value)}</span>,
    },
    {
      title: t("field.createdAt"),
      dataIndex: "createdAt",
      width: 150,
      render: (value: string) =>
        value ? dayjs(value).format("YYYY-MM-DD HH:mm") : "—",
    },
    {
      title: t("admin.actions"),
      key: "actions",
      width: 100,
      align: "right",
      render: (_, record) => (
        <Button
          size="small"
          icon={<DownloadOutlined />}
          loading={downloading === record.id}
          onClick={() => void download(record)}
        >
          {t("common.download")}
        </Button>
      ),
    },
  ];

  return (
    <section className="admin-detail-card">
      <div className="admin-toolbar">
        <Input.Search
          allowClear
          placeholder={t("admin.searchResumes")}
          style={{ width: 240 }}
          onSearch={(value) => {
            setSearch(value);
            load(value);
          }}
        />
        <span className="admin-muted">{rows.length} {t("common.items")}</span>
      </div>
      <Table<Resume>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        scroll={{ x: 860 }}
        locale={{ emptyText: t("admin.noResumes") }}
      />
    </section>
  );
}
