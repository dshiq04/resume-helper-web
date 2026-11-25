import { useEffect, useState } from "react";
import { App as AntdApp, Button, Checkbox, DatePicker, Modal } from "antd";
import type { Dayjs } from "dayjs";
import { importApi } from "../api/import";
import type { Category } from "../api/types";
import { useI18n } from "../i18n";
import { errorMessage } from "../utils/errors";

/** 导出类型展示顺序：全部之后依次为秋招、春招、实习、社招。 */
const EXPORT_CATEGORIES: Category[] = ["autumn", "spring", "internship", "social"];

interface Props {
  open: boolean;
  onClose: () => void;
}

/** 导出投递台账：按投递时间范围与投递类型筛选后下载 Excel。 */
export function ExportModal({ open, onClose }: Props) {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [categories, setCategories] = useState<Category[]>([...EXPORT_CATEGORIES]);
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (open) {
      setCategories([...EXPORT_CATEGORIES]);
      setRange(null);
    }
  }, [open]);

  const allChecked = categories.length === EXPORT_CATEGORIES.length;
  const partiallyChecked = categories.length > 0 && !allChecked;

  const toggleAll = () => {
    setCategories(allChecked ? [] : [...EXPORT_CATEGORIES]);
  };

  const toggleCategory = (category: Category, checked: boolean) => {
    setCategories((current) =>
      checked
        ? [...current.filter((item) => item !== category), category]
        : current.filter((item) => item !== category),
    );
  };

  const submit = async () => {
    if (categories.length === 0) {
      message.warning(t("export.emptyTypes"));
      return;
    }
    setLoading(true);
    try {
      await importApi.exportApplications({
        categories,
        from: range ? range[0].startOf("day").format("YYYY-MM-DD") : undefined,
        to: range ? range[1].endOf("day").format("YYYY-MM-DD") : undefined,
      });
      message.success(t("export.success"));
      onClose();
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
      title={t("export.title")}
      width={520}
      footer={
        <div className="modal-actions">
          <Button onClick={onClose}>{t("common.cancel")}</Button>
          <Button type="primary" loading={loading} onClick={() => void submit()}>
            {t("common.confirm")}
          </Button>
        </div>
      }
    >
      <p className="modal-lead">{t("export.hint")}</p>

      <div className="export-field">
        <div className="export-label">{t("export.range")}</div>
        <DatePicker.RangePicker
          className="export-range"
          allowClear
          value={range}
          onChange={(values) => {
            if (values && values[0] && values[1]) {
              setRange([values[0], values[1]]);
            } else {
              setRange(null);
            }
          }}
          placeholder={[t("dash.startDate"), t("dash.endDate")]}
          format="YYYY-MM-DD"
        />
        <p className="export-hint">{t("export.rangeHint")}</p>
      </div>

      <div className="export-field">
        <div className="export-label">{t("export.types")}</div>
        <div className="export-types">
          <Checkbox checked={allChecked} indeterminate={partiallyChecked} onChange={toggleAll}>
            {t("common.all")}
          </Checkbox>
          {EXPORT_CATEGORIES.map((category) => (
            <Checkbox
              key={category}
              checked={categories.includes(category)}
              onChange={(event) => toggleCategory(category, event.target.checked)}
            >
              {t(`category.${category}`)}
            </Checkbox>
          ))}
        </div>
      </div>
    </Modal>
  );
}
