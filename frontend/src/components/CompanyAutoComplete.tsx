import { useEffect, useState } from "react";
import { AutoComplete } from "antd";
import { applicationApi } from "../api/applications";

export function CompanyAutoComplete({
  value,
  onChange,
  placeholder,
}: {
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
}) {
  const [options, setOptions] = useState<{ value: string }[]>([]);

  useEffect(() => {
    const keyword = value?.trim() ?? "";
    const timer = window.setTimeout(() => {
      void applicationApi
        .companies(keyword)
        .then((result) => setOptions(result.items.map((item) => ({ value: item }))))
        .catch(() => setOptions([]));
    }, 220);
    return () => window.clearTimeout(timer);
  }, [value]);

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
