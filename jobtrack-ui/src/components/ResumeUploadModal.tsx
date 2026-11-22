import { useEffect, useState } from "react";
import { App as AntdApp, Button, Form, Input, Modal, Select, Upload } from "antd";
import { InboxOutlined } from "@ant-design/icons";
import { adminApi } from "../api/admin";
import { resumeApi } from "../api/resumes";
import type { Resume } from "../api/types";
import { useI18n } from "../i18n";
import { errorMessage } from "../utils/errors";
import { formatSize } from "../utils/format";

interface Props {
  open: boolean;
  editing: Resume | null;
  /** 管理员代指定用户上传/编辑时传入用户 ID，请求会走管理员接口。 */
  userId?: number;
  onClose: () => void;
  onSaved: () => void;
}

interface Values {
  name: string;
  tags: string[];
}

const SUPPORTED_EXTENSIONS = ["pdf", "html", "pptx", "docx", "png", "jpg", "jpeg", "md"];

function fileExtension(fileName: string): string {
  const dot = fileName.lastIndexOf(".");
  return dot < 0 ? "" : fileName.slice(dot + 1).toLowerCase();
}

export function ResumeUploadModal({ open, editing, userId, onClose, onSaved }: Props) {
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
    const name = values.name?.trim() ?? "";
    if (!name) {
      message.error(t("resume.nameRequired"));
      return;
    }
    if (name.length > 255) {
      message.error(t("resume.nameTooLong"));
      return;
    }
    if (!editing) {
      if (!file) {
        message.warning(t("resume.file"));
        return;
      }
      if (file.size <= 0) {
        message.error(t("resume.fileEmpty"));
        return;
      }
      if (!SUPPORTED_EXTENSIONS.includes(fileExtension(file.name))) {
        message.error(t("resume.fileType"));
        return;
      }
    }

    setSaving(true);
    try {
      if (editing) {
        if (userId) {
          await adminApi.updateResume(userId, editing.id, { name, tags: values.tags });
        } else {
          await resumeApi.update(editing.id, { name, tags: values.tags });
        }
      } else if (userId) {
        await adminApi.createResume(userId, name, values.tags ?? [], file as File);
      } else {
        await resumeApi.create(name, values.tags ?? [], file as File);
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
      <Form form={form} layout="vertical" onFinish={submit}>
        <Form.Item
          name="name"
          label={t("resume.name")}
          rules={[
            { required: true, whitespace: true, message: t("resume.nameRequired") },
            { max: 255, message: t("resume.nameTooLong") },
          ]}
        >
          <Input maxLength={255} placeholder={t("resume.namePlaceholder")} />
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
            accept=".pdf,.html,.pptx,.docx,.png,.jpg,.jpeg,.md"
            maxCount={1}
            beforeUpload={(selected) => {
              if (selected.size <= 0) {
                setFile(null);
                message.error(t("resume.fileEmpty"));
                return Upload.LIST_IGNORE;
              }
              if (!SUPPORTED_EXTENSIONS.includes(fileExtension(selected.name))) {
                setFile(null);
                message.error(t("resume.fileType"));
                return Upload.LIST_IGNORE;
              }
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
