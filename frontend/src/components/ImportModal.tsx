import { useEffect, useState } from "react";
import { App as AntdApp, Alert, Button, Modal, Upload } from "antd";
import { InboxOutlined } from "@ant-design/icons";
import { excelApi } from "../api/excel";
import type { ImportResult } from "../api/types";
import { useI18n } from "../i18n";
import { errorMessage } from "../utils/errors";
import { notifyDataChanged } from "../utils/format";

interface Props {
  open: boolean;
  onClose: () => void;
  onImported: () => void;
}

export function ImportModal({ open, onClose, onImported }: Props) {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (open) {
      setFile(null);
      setResult(null);
    }
  }, [open]);

  const submit = async () => {
    if (!file) {
      message.warning(t("import.choose"));
      return;
    }
    setLoading(true);
    try {
      const value = await excelApi.importFile(file);
      setResult(value);
      message.success(t("import.result", { imported: value.imported, skipped: value.skipped }));
      notifyDataChanged();
      onImported();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={t("import.title")}
      width={540}
      footer={
        <div className="modal-actions">
          <Button onClick={() => void excelApi.downloadTemplate()}>{t("import.download")}</Button>
          <Button onClick={onClose}>{t("common.close")}</Button>
          <Button type="primary" loading={loading} onClick={() => void submit()}>
            {t("import.title")}
          </Button>
        </div>
      }
    >
      <p className="modal-lead">{t("import.hint")}</p>
      <Upload.Dragger
        accept=".xlsx,.xlsm"
        maxCount={1}
        beforeUpload={(selected) => {
          setFile(selected);
          setResult(null);
          return false;
        }}
        onRemove={() => setFile(null)}
        fileList={file ? [{ uid: "selected", name: file.name, size: file.size, status: "done" }] : []}
        className="import-dragger"
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">{t("import.choose")}</p>
      </Upload.Dragger>

      {result && (
        <div className="import-result">
          <Alert
            type={result.skipped > 0 ? "warning" : "success"}
            showIcon
            message={t("import.result", {
              imported: result.imported,
              skipped: result.skipped,
            })}
          />
          {result.errors.length > 0 && (
            <div className="import-errors">
              <strong>{t("import.errors")}</strong>
              <ul>
                {result.errors.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}
