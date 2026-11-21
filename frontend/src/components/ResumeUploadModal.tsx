import { useEffect, useState } from "react";
import { App as AntdApp, Button, Form, Input, Modal, Select, Upload } from "antd";
import { InboxOutlined } from "@ant-design/icons";
import { resumeApi } from "../api/resumes";
import type { Resume } from "../api/types";
import { useI18n } from "../i18n";
import { errorMessage } from "../utils/errors";
import { formatSize } from "../utils/format";

interface Props {
  open: boolean;
  editing: Resume | null;
  onClose: () => void;
  onSaved: () => void;
}

interface Values {
  name: string;
  tags: string[];
}

export function ResumeUploadModal({ open, editing, onClose, onSaved }: Props) {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<Values>();
  const [file, setFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setFile(null);
    form.resetFields();
    if (editing) {
      form.setFieldsValue({ name: editing.name, tags: editing.tags });
    }
  }, [editing, form, open]);

  const submit = async (values: Values) => {
    setSaving(true);
    try {
      if (editing) {
        await resumeApi.update(editing.id, { name: values.name, tags: values.tags });
      } else {
        if (!file) {
          message.warning(t("resume.file"));
          setSaving(false);
          return;
        }
        await resumeApi.create(values.name || file.name, values.tags ?? [], file);
      }
      message.success(t("settings.saved"));
      onSaved();
      onClose();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={editing ? t("resume.editTitle") : t("resume.uploadTitle")}
      width={520}
      footer={
        <div className="modal-actions">
          <Button onClick={onClose}>{t("common.cancel")}</Button>
          <Button type="primary" loading={saving} onClick={() => form.submit()}>
            {t("common.save")}
          </Button>
        </div>
      }
    >
      <Form form={form} layout="vertical" onFinish={submit} requiredMark={false}>
        <Form.Item
          name="name"
          label={t("resume.name")}
          rules={[{ required: !editing, message: t("resume.name") }]}
        >
          <Input placeholder={t("resume.namePlaceholder")} />
        </Form.Item>
        <Form.Item name="tags" label={t("field.tags")}>
          <Select
            mode="tags"
            tokenSeparators={[",", "，"]}
            placeholder={t("resume.tagsPlaceholder")}
            open={false}
            suffixIcon={null}
          />
        </Form.Item>
        {!editing && (
          <Upload.Dragger
            accept=".pdf,.docx,.pptx,.xlsx,.html,.htm,.png,.jpg,.jpeg,.webp,.md,.markdown"
            maxCount={1}
            beforeUpload={(selected) => {
              setFile(selected);
              if (!form.getFieldValue("name")) {
                form.setFieldValue("name", selected.name.replace(/\.[^.]+$/, ""));
              }
              return false;
            }}
            onRemove={() => setFile(null)}
            fileList={
              file
                ? [{ uid: "resume", name: `${file.name} · ${formatSize(file.size)}`, status: "done" }]
                : []
            }
            className="import-dragger"
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">{t("resume.drop")}</p>
          </Upload.Dragger>
        )}
      </Form>
    </Modal>
  );
}
