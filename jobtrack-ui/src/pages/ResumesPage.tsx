import { lazy, Suspense, useCallback, useEffect, useState } from "react";
import { App as AntdApp, Button, Empty, Input, Popconfirm, Spin } from "antd";
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  FileTextOutlined,
  PlusOutlined,
  StarFilled,
  StarOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import { downloadBlob } from "../api/client";
import { resumeApi } from "../api/resumes";
import type { Resume } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { ResumeUploadModal } from "../components/ResumeUploadModal";
import { useI18n } from "../i18n";
import { errorMessage } from "../utils/errors";
import { formatDate, formatSize, notifyDataChanged } from "../utils/format";

// 文件预览依赖较多（PDF / Office / 图片解码器等），按需加载，避免拖慢首屏。
const ResumePreviewModal = lazy(() =>
  import("../components/ResumePreviewModal").then((module) => ({
    default: module.ResumePreviewModal,
  })),
);

export function ResumesPage() {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [items, setItems] = useState<Resume[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [editing, setEditing] = useState<Resume | null>(null);
  const [preview, setPreview] = useState<Resume | null>(null);

  const load = useCallback(async (keyword = search) => {
    setLoading(true);
    try {
      const result = await resumeApi.list(keyword);
      setItems(result.items);
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setLoading(false);
    }
  }, [message, search]);

  useEffect(() => {
    void load();
    // 首次进入加载即可，搜索由输入框回车触发。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const togglePin = async (resume: Resume) => {
    try {
      await resumeApi.update(resume.id, { pinned: !resume.pinned });
      await load();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const remove = async (resume: Resume) => {
    try {
      await resumeApi.remove(resume.id);
      message.success(t("common.delete"));
      notifyDataChanged();
      await load();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const download = async (resume: Resume) => {
    try {
      const blob = await resumeApi.fetchFile(resume.id);
      downloadBlob(blob, resume.fileName);
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  return (
    <>
      <PageHeader
        eyebrow={t("nav.resumes")}
        title={t("resume.title")}
        subtitle={t("resume.subtitle")}
        actions={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditing(null);
              setUploadOpen(true);
            }}
          >
            {t("resume.upload")}
          </Button>
        }
      />

      <div className="ledger-toolbar compact-toolbar">
        <Input.Search
          className="toolbar-search"
          allowClear
          placeholder={t("resume.search")}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          onSearch={(value) => void load(value)}
        />
        <span className="toolbar-count">
          {items.length} {t("common.items")}
        </span>
      </div>

      {loading ? (
        <div className="page-loading">
          <Spin />
        </div>
      ) : items.length === 0 ? (
        <Empty
          image={<div className="empty-mark">历</div>}
          description={
            <div className="empty-copy">
              <strong>{t("resume.empty")}</strong>
              <span>{t("resume.emptyHint")}</span>
            </div>
          }
        >
          <Button
            type="primary"
            icon={<UploadOutlined />}
            onClick={() => {
              setEditing(null);
              setUploadOpen(true);
            }}
          >
            {t("resume.upload")}
          </Button>
        </Empty>
      ) : (
        <div className="resume-grid">
          {items.map((resume) => (
            <article key={resume.id} className={`resume-card${resume.pinned ? " is-pinned" : ""}`}>
              <div className="resume-card-head">
                <span className={`file-badge type-${resume.fileType}`}>
                  {resume.fileType.toUpperCase().slice(0, 4)}
                </span>
                <button
                  type="button"
                  className={`pin-button${resume.pinned ? " is-pinned" : ""}`}
                  onClick={() => void togglePin(resume)}
                  title={resume.pinned ? t("resume.unpin") : t("resume.pin")}
                >
                  {resume.pinned ? <StarFilled /> : <StarOutlined />}
                </button>
              </div>
              <h3>{resume.name}</h3>
              <p className="resume-file">
                <FileTextOutlined /> {resume.fileName}
              </p>
              <p className="resume-meta">
                {formatSize(resume.fileSize)} · {formatDate(resume.createdAt)}
              </p>
              <div className="tag-row">
                {resume.tags.map((tag) => (
                  <span key={tag} className="text-chip">
                    {tag}
                  </span>
                ))}
              </div>
              <div className="resume-actions">
                <Button size="small" icon={<EyeOutlined />} onClick={() => setPreview(resume)}>
                  {t("resume.preview")}
                </Button>
                <Button size="small" icon={<DownloadOutlined />} onClick={() => void download(resume)}>
                  {t("resume.download")}
                </Button>
                <Button
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => {
                    setEditing(resume);
                    setUploadOpen(true);
                  }}
                />
                <Popconfirm
                  title={t("resume.deleteConfirm", { name: resume.name })}
                  onConfirm={() => void remove(resume)}
                  okText={t("common.confirm")}
                  cancelText={t("common.cancel")}
                >
                  <Button size="small" danger icon={<DeleteOutlined />} />
                </Popconfirm>
              </div>
            </article>
          ))}
        </div>
      )}

      <ResumeUploadModal
        open={uploadOpen}
        editing={editing}
        onClose={() => setUploadOpen(false)}
        onSaved={() => {
          void load();
          notifyDataChanged();
        }}
      />
      {preview && (
        <Suspense fallback={null}>
          <ResumePreviewModal resume={preview} onClose={() => setPreview(null)} />
        </Suspense>
      )}
    </>
  );
}
