import { useEffect, useState } from "react";
import { Button, Modal, Spin } from "antd";
import DOMPurify from "dompurify";
import mammoth from "mammoth";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { resumeApi } from "../api/resumes";
import type { Resume } from "../api/types";
import { useI18n } from "../i18n";
import { downloadBlob } from "../api/client";
import { errorMessage } from "../utils/errors";

export function ResumePreviewModal({
  resume,
  onClose,
}: {
  resume: Resume | null;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const [loading, setLoading] = useState(false);
  const [url, setUrl] = useState<string | null>(null);
  const [markdown, setMarkdown] = useState<string | null>(null);
  const [docHtml, setDocHtml] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!resume) {
      setUrl(null);
      setMarkdown(null);
      setDocHtml(null);
      setError(null);
      return;
    }
    let revoked: string | null = null;
    let cancelled = false;
    setLoading(true);
    setUrl(null);
    setMarkdown(null);
    setDocHtml(null);
    setError(null);

    void (async () => {
      try {
        const blob = await resumeApi.fetchFile(resume.id);
        if (cancelled) return;
        if (resume.fileType === "pdf" || ["png", "jpg", "jpeg", "webp"].includes(resume.fileType)) {
          const objectUrl = URL.createObjectURL(blob);
          revoked = objectUrl;
          setUrl(objectUrl);
        } else if (resume.fileType === "md" || resume.fileType === "markdown") {
          setMarkdown(await blob.text());
        } else if (resume.fileType === "docx") {
          const buffer = await blob.arrayBuffer();
          const result = await mammoth.convertToHtml({ arrayBuffer: buffer });
          if (!cancelled) {
            setDocHtml(DOMPurify.sanitize(result.value));
          }
        } else {
          setError(t("resume.previewUnsupported"));
        }
      } catch (exception) {
        if (!cancelled) setError(errorMessage(exception));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
      if (revoked) URL.revokeObjectURL(revoked);
    };
  }, [resume, t]);

  const download = async () => {
    if (!resume) return;
    const blob = await resumeApi.fetchFile(resume.id);
    downloadBlob(blob, resume.fileName);
  };

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
      ) : url && resume?.fileType === "pdf" ? (
        <iframe title={resume.name} src={url} className="preview-frame" />
      ) : url ? (
        <div className="preview-image">
          <img src={url} alt={resume?.name} />
        </div>
      ) : markdown !== null ? (
        <article className="markdown-body">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{markdown}</ReactMarkdown>
        </article>
      ) : docHtml !== null ? (
        <article className="markdown-body" dangerouslySetInnerHTML={{ __html: docHtml }} />
      ) : null}
    </Modal>
  );
}
