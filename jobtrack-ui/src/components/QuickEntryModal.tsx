import { useEffect, useState } from "react";
import { App as AntdApp, Button, Form, Input, Modal, Select } from "antd";
import { applicationApi } from "../api/applications";
import { configApi } from "../api/config";
import type { Category, PublicConfig } from "../api/types";
import { useI18n } from "../i18n";
import { errorMessage } from "../utils/errors";
import { notifyDataChanged } from "../utils/format";
import { CompanyAutoComplete } from "./CompanyAutoComplete";

interface Props {
  open: boolean;
  defaultCategory: Category;
  onClose: () => void;
  onSaved: () => void;
}

interface Values {
  companyName: string;
  link: string;
  category: Category;
  position?: string;
}

export function QuickEntryModal({ open, defaultCategory, onClose, onSaved }: Props) {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<Values>();
  const [saving, setSaving] = useState(false);
  const [config, setConfig] = useState<PublicConfig | null>(null);

  useEffect(() => {
    if (open) {
      form.setFieldValue("category", defaultCategory);
      void configApi.get().then(setConfig).catch(() => setConfig(null));
    }
  }, [defaultCategory, form, open]);

  const submit = async (values: Values) => {
    setSaving(true);
    try {
      await applicationApi.create({
        ...values,
        status: "screening",
        year: new Date().getFullYear(),
      });
      message.success(t("quick.success"));
      notifyDataChanged();
      onSaved();
      form.resetFields();
      form.setFieldValue("category", defaultCategory);
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
      title={t("quick.title")}
      footer={null}
      width={460}
      className="quick-modal"
    >
      <p className="modal-lead">{t("quick.hint")}</p>
      <Form form={form} layout="vertical" onFinish={submit}>
        <Form.Item
          name="companyName"
          label={t("field.company")}
          rules={[{ required: true, message: t("form.companyRequired") }]}
        >
          <CompanyAutoComplete placeholder={t("form.companyPlaceholder")} />
        </Form.Item>
        <Form.Item
          name="link"
          label={t("field.link")}
          rules={[{ required: true, message: t("form.linkRequired") }]}
        >
          <Input placeholder={t("form.linkPlaceholder")} />
        </Form.Item>
        <div className="form-grid two">
          <Form.Item name="category" label={t("field.category")}>
            <Select
              options={(config?.categories ?? ["spring", "autumn", "internship", "social"]).map(
                (value) => ({ value, label: t(`category.${value}`) }),
              )}
            />
          </Form.Item>
          <Form.Item name="position" label={t("field.position")}>
            <Input placeholder={t("common.optional")} />
          </Form.Item>
        </div>
        <div className="modal-actions">
          <Button onClick={onClose}>{t("common.close")}</Button>
          <Button type="primary" htmlType="submit" loading={saving}>
            {t("common.add")}
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
