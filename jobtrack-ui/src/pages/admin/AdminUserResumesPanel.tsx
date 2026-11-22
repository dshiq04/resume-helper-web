import { useEffect, useState } from "react";
import { App as AntdApp, Button, Input, Popconfirm, Table, Tag, Tooltip } from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  PlusOutlined,
} from "@ant-design/icons";
import dayjs from "dayjs";
import { adminApi } from "../../api/admin";
import { downloadBlob } from "../../api/client";
import type { Resume } from "../../api/types";
import { ResumeUploadModal } from "../../components/ResumeUploadModal";
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
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Resume | null>(null);

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

  const openUpload = () => {
    setEditing(null);
    setFormOpen(true);
  };

  const openEdit = (record: Resume) => {
    setEditing(record);
    setFormOpen(true);
  };

  const remove = async (record: Resume) => {
    try {
      await adminApi.deleteResume(userId, record.id);
      message.success(t("admin.resumeDeleted"));
      load();
    } catch (error) {
      message.error(errorMessage(error));
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
      width: 126,
      align: "right",
      render: (_, record) => (
        <div className="row-actions">
          <Tooltip title={t("common.download")}>
            <button
              type="button"
              className="ghost-icon tiny"
              disabled={downloading === record.id}
              onClick={() => void download(record)}
            >
              <DownloadOutlined />
            </button>
          </Tooltip>
          <Tooltip title={t("common.edit")}>
            <button
              type="button"
              className="ghost-icon tiny"
              onClick={() => openEdit(record)}
            >
              <EditOutlined />
            </button>
          </Tooltip>
          <Tooltip title={t("common.delete")}>
            <Popconfirm
              title={t("admin.deleteResumeConfirm", { name: record.name })}
              onConfirm={() => void remove(record)}
              okText={t("common.confirm")}
              cancelText={t("common.cancel")}
            >
              <button type="button" className="ghost-icon tiny danger">
                <DeleteOutlined />
              </button>
            </Popconfirm>
          </Tooltip>
        </div>
      ),
    },
  ];

  return (
    <section className="admin-detail-card admin-card">
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
        <div className="admin-toolbar-right">
          <Button type="primary" icon={<PlusOutlined />} onClick={openUpload}>
            {t("resume.upload")}
          </Button>
        </div>
      </div>
      <Table<Resume>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        scroll={{ x: 1000 }}
        locale={{ emptyText: t("admin.noResumes") }}
      />

      <ResumeUploadModal
        open={formOpen}
        editing={editing}
        userId={userId}
        onClose={() => setFormOpen(false)}
        onSaved={() => load()}
      />
    </section>
  );
}
