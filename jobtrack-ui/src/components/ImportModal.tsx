import { useEffect, useState } from "react";
import { App as AntdApp, Alert, Button, Modal, Upload } from "antd";
import { InboxOutlined } from "@ant-design/icons";
import { importApi } from "../api/import";
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
      const value = await importApi.importFile(file);
      setResult(value);
      message.success(
        t("import.result", { imported: value.imported, skipped: value.skipped }),
      );
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
      width={560}
      footer={
        <div className="modal-actions">
          <Button onClick={() => void importApi.downloadTemplate()}>{t("import.download")}</Button>
          <Button onClick={onClose}>{t("common.close")}</Button>
          <Button type="primary" loading={loading} onClick={() => void submit()}>
            {t("import.title")}
          </Button>
        </div>
      }
    >
      <p className="modal-lead">{t("import.hint")}</p>
      <Upload.Dragger
        className="import-dragger"
        accept=".xlsx"
        maxCount={1}
        showUploadList={false}
        beforeUpload={(selected) => {
          if (!selected.name.toLowerCase().endsWith(".xlsx")) {
            message.error(t("import.onlyXlsx"));
            return Upload.LIST_IGNORE;
          }
          setFile(selected);
          setResult(null);
          return false;
        }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">
          {file ? file.name : t("import.choose")}
        </p>
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
                {result.errors.map((error) => (
                  <li key={error}>{error}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}
