import { useEffect, useMemo, useState } from "react";
import {
  App as AntdApp,
  AutoComplete,
  Button,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
} from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { applicationApi } from "../api/applications";
import { configApi } from "../api/config";
import type { Application, ApplicationInput, Category, PublicConfig, Status } from "../api/types";
import { useI18n } from "../i18n";
import { errorMessage } from "../utils/errors";
import { notifyDataChanged } from "../utils/format";
import { CompanyAutoComplete } from "./CompanyAutoComplete";

const FALLBACK_CHANNELS = ["官网", "内推", "Boss直聘", "牛客", "猎聘", "实习僧", "拉勾", "其他"];

interface Props {
  open: boolean;
  editing: Application | null;
  defaultCategory?: Category;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  companyName: string;
  link: string;
  category: Category;
  status: Status;
  year: number;
  position?: string;
  channel?: string;
  city?: string;
  salary?: string;
  contact?: string;
  contactInfo?: string;
  workMode?: string;
  priority?: string;
  interviewRound?: number;
  nextActionAt?: Dayjs;
  notes?: string;
  pinned?: boolean;
}

export function ApplicationFormDrawer({
  open,
  editing,
  defaultCategory,
  onClose,
  onSaved,
}: Props) {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<FormValues>();
  const [config, setConfig] = useState<PublicConfig | null>(null);

  useEffect(() => {
    if (open) {
      void configApi.get().then(setConfig).catch(() => setConfig(null));
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue({
        ...editing,
        year: editing.year,
        nextActionAt: editing.nextActionAt ? dayjs(editing.nextActionAt) : undefined,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        category: defaultCategory ?? "spring",
        status: "screening",
        year: new Date().getFullYear(),
      });
    }
  }, [defaultCategory, editing, form, open]);

  const categoryOptions = useMemo(
    () =>
      (config?.categories ?? ["spring", "autumn", "internship", "social"]).map((value) => ({
        value,
        label: t(`category.${value}`),
      })),
    [config?.categories, t],
  );
  const statusOptions = useMemo(
    () =>
      (config?.statuses ?? ["pending", "screening", "written", "interview", "offer", "rejected"]).map(
        (value) => ({ value, label: t(`status.${value}`) }),
      ),
    [config?.statuses, t],
  );
  const channelOptions = useMemo(
    () => (config?.channels ?? FALLBACK_CHANNELS).map((value) => ({ value })),
    [config?.channels],
  );
  const priorityOptions = [
    { value: "high", label: t("priority.high") },
    { value: "medium", label: t("priority.medium") },
    { value: "low", label: t("priority.low") },
  ];
  const workModeOptions = [
    { value: "onsite", label: t("workMode.onsite") },
    { value: "hybrid", label: t("workMode.hybrid") },
    { value: "remote", label: t("workMode.remote") },
  ];

  const currentYear = new Date().getFullYear();
  const years = Array.from({ length: 9 }, (_, index) => currentYear + 2 - index);

  const submit = async (values: FormValues) => {
    const input: ApplicationInput = {
      companyName: values.companyName,
      link: values.link,
      category: values.category,
      status: values.status,
      year: values.year,
      position: values.position,
      channel: values.channel,
      city: values.city,
      salary: values.salary,
      contact: values.contact,
      contactInfo: values.contactInfo,
      workMode: values.workMode,
      priority: values.priority,
      interviewRound: values.interviewRound,
      nextActionAt: values.nextActionAt?.toISOString(),
      notes: values.notes,
      pinned: values.pinned,
    };
    try {
      if (editing) {
        await applicationApi.update(editing.id, input);
      } else {
        await applicationApi.create(input);
      }
      message.success(t("settings.saved"));
      notifyDataChanged();
      onSaved();
      onClose();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={520}
      title={editing ? t("form.editTitle") : t("form.createTitle")}
      className="record-drawer"
      footer={
        <div className="drawer-footer">
          <span className="drawer-hint">{t("form.categoryHint")}</span>
          <Button onClick={onClose}>{t("common.cancel")}</Button>
          <Button type="primary" onClick={() => form.submit()}>
            {t("common.save")}
          </Button>
        </div>
      }
    >
      <Form form={form} layout="vertical" onFinish={submit}>
        <div className="form-section-label">{t("form.basic")}</div>
        <Form.Item
          name="companyName"
          label={t("field.company")}
          rules={[{ required: true, whitespace: true, message: t("form.companyRequired") }]}
        >
          <CompanyAutoComplete placeholder={t("form.companyPlaceholder")} />
        </Form.Item>
        <Form.Item
          name="link"
          label={t("field.link")}
          rules={[{ required: true, whitespace: true, message: t("form.linkRequired") }]}
        >
          <Input placeholder={t("form.linkPlaceholder")} />
        </Form.Item>
        <div className="form-grid two">
          <Form.Item
            name="category"
            label={t("field.category")}
            rules={[{ required: true, message: t("form.categoryRequired") }]}
          >
            <Select options={categoryOptions} />
          </Form.Item>
          <Form.Item
            name="year"
            label={t("field.year")}
            rules={[
              { required: true, message: t("form.yearRequired") },
              {
                validator: (_, value) => {
                  if (value === undefined || value === null) {
                    return Promise.resolve();
                  }
                  if (!Number.isInteger(value) || value < 2000 || value > 2100) {
                    return Promise.reject(new Error(t("form.yearInvalid")));
                  }
                  return Promise.resolve();
                },
              },
            ]}
          >
            <Select options={years.map((year) => ({ value: year, label: String(year) }))} />
          </Form.Item>
        </div>
        <div className="form-grid two">
          <Form.Item name="position" label={t("field.position")}>
            <Input placeholder={t("form.positionPlaceholder")} />
          </Form.Item>
          <Form.Item
            name="status"
            label={t("field.status")}
            rules={[{ required: true, message: t("form.statusRequired") }]}
          >
            <Select options={statusOptions} />
          </Form.Item>
        </div>

        <div className="form-section-label">{t("form.extra")}</div>
        <div className="form-grid two">
          <Form.Item name="channel" label={t("field.channel")}>
            <AutoComplete options={channelOptions} placeholder={t("common.optional")} />
          </Form.Item>
          <Form.Item name="city" label={t("field.city")}>
            <Input placeholder={t("common.optional")} />
          </Form.Item>
          <Form.Item name="salary" label={t("field.salary")}>
            <Input placeholder="20-30K" />
          </Form.Item>
          <Form.Item name="priority" label={t("field.priority")}>
            <Select allowClear options={priorityOptions} placeholder={t("common.optional")} />
          </Form.Item>
          <Form.Item name="workMode" label={t("field.workMode")}>
            <Select allowClear options={workModeOptions} placeholder={t("common.optional")} />
          </Form.Item>
          <Form.Item name="interviewRound" label={t("field.interviewRound")}>
            <InputNumber min={0} max={20} style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item name="contact" label={t("field.contact")}>
            <Input placeholder={t("common.optional")} />
          </Form.Item>
          <Form.Item name="contactInfo" label={t("field.contactInfo")}>
            <Input placeholder={t("common.optional")} />
          </Form.Item>
        </div>
        <Form.Item name="nextActionAt" label={t("field.nextAction")}>
          <DatePicker showTime style={{ width: "100%" }} placeholder={t("common.optional")} />
        </Form.Item>
        <Form.Item name="notes" label={t("field.notes")}>
          <Input.TextArea rows={4} maxLength={2000} showCount placeholder={t("common.optional")} />
        </Form.Item>
      </Form>
    </Drawer>
  );
}
