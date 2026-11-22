import { useEffect, useState } from "react";
import { AutoComplete } from "antd";
import { adminApi } from "../api/admin";
import { applicationApi } from "../api/applications";

export function CompanyAutoComplete({
  value,
  onChange,
  placeholder,
  userId,
}: {
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  /** 管理员代指定用户编辑时，从该用户历史投递中补全公司名。 */
  userId?: number;
}) {
  const [options, setOptions] = useState<{ value: string }[]>([]);

  useEffect(() => {
    const keyword = value?.trim() ?? "";
    const timer = window.setTimeout(() => {
      const request = userId
        ? adminApi.applicationCompanies(userId, keyword)
        : applicationApi.companies(keyword);
      void request
        .then((result) => setOptions(result.items.map((item) => ({ value: item }))))
        .catch(() => setOptions([]));
    }, 220);
    return () => window.clearTimeout(timer);
  }, [userId, value]);

  return (
    <AutoComplete
      value={value}
      options={options}
      onChange={onChange}
      placeholder={placeholder}
      filterOption={false}
    />
  );
}
