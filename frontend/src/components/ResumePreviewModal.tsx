import { useEffect, useState } from "react";
import { Button, Modal, Spin } from "antd";
import { FilePreviewEmbed } from "@eternalheart/react-file-preview";
import "@eternalheart/react-file-preview/style.css";
import { resumeApi } from "../api/resumes";
import type { Resume } from "../api/types";
import { useI18n } from "../i18n";
import { downloadBlob } from "../api/client";
import { useAppSelector } from "../store/hooks";
import { errorMessage } from "../utils/errors";

export function ResumePreviewModal({
  resume,
  onClose,
}: {
  resume: Resume | null;
  onClose: () => void;
}) {
  const { t, lang } = useI18n();
  const themeMode = useAppSelector((state) => state.ui.themeMode);
  const [loading, setLoading] = useState(false);
  const [files, setFiles] = useState<File[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!resume) {
      setFiles([]);
      setError(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setFiles([]);
    setError(null);

    void (async () => {
      try {
        const blob = await resumeApi.fetchFile(resume.id);
        if (cancelled) return;
        setFiles([
          new File([blob], resume.fileName, {
            type: blob.type || "application/octet-stream",
          }),
        ]);
      } catch (exception) {
        if (!cancelled) setError(errorMessage(exception));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [resume]);

  const download = async () => {
    if (!resume) return;
    const blob = await resumeApi.fetchFile(resume.id);
    downloadBlob(blob, resume.fileName);
  };

  const previewLocale = lang === "zh-CN" ? "zh-CN" : lang === "zh-TW" ? "zh-TW" : "en-US";

  return (
    <Modal
      open={!!resume}
      onCancel={onClose}
      title={resume?.name}
      width={940}
      className="preview-modal"
      footer={
        <div className="modal-actions">
          <Button onClick={onClose}>{t("common.close")}</Button>
          <Button type="primary" onClick={() => void download()}>
            {t("resume.download")}
          </Button>
        </div>
      }
    >
      {loading ? (
        <div className="preview-loading">
          <Spin />
          <span>{t("resume.previewLoading")}</span>
        </div>
      ) : error ? (
        <div className="preview-error">{error}</div>
      ) : files.length > 0 ? (
        <div className="preview-embed">
          <FilePreviewEmbed
            files={files}
            currentIndex={0}
            theme={themeMode}
            locale={previewLocale}
            showDownload={false}
            showClose={false}
            showNavigation={false}
          />
        </div>
      ) : null}
    </Modal>
  );
}
