export interface CountryCode {
  value: string;
  label: string;
}

/** 常用国家/地区电话区号，可按需继续补充。 */
export const COUNTRY_CODES: CountryCode[] = [
  { value: "+86", label: "CN 中国 +86" },
  { value: "+852", label: "HK 中国香港 +852" },
  { value: "+853", label: "MO 中国澳门 +853" },
  { value: "+886", label: "TW 中国台湾 +886" },
  { value: "+1", label: "US/CA 美国/加拿大 +1" },
  { value: "+44", label: "GB 英国 +44" },
  { value: "+81", label: "JP 日本 +81" },
  { value: "+82", label: "KR 韩国 +82" },
  { value: "+65", label: "SG 新加坡 +65" },
  { value: "+61", label: "AU 澳大利亚 +61" },
  { value: "+49", label: "DE 德国 +49" },
  { value: "+33", label: "FR 法国 +33" },
  { value: "+39", label: "IT 意大利 +39" },
  { value: "+34", label: "ES 西班牙 +34" },
  { value: "+7", label: "RU 俄罗斯 +7" },
  { value: "+91", label: "IN 印度 +91" },
  { value: "+55", label: "BR 巴西 +55" },
  { value: "+27", label: "ZA 南非 +27" },
];

const SORTED_CODES = [...COUNTRY_CODES].sort((a, b) => b.value.length - a.value.length);

/** 把完整手机号拆成区号与本地号码。 */
export function splitPhone(value?: string | null): { countryCode: string; phone: string } {
  const raw = (value ?? "").trim();
  if (!raw) {
    return { countryCode: "+86", phone: "" };
  }
  const matched = SORTED_CODES.find((item) => raw.startsWith(item.value));
  if (!matched) {
    return { countryCode: "+86", phone: raw.replace(/^[+]/, "") };
  }
  return {
    countryCode: matched.value,
    phone: raw.slice(matched.value.length).replace(/^[\s-]+/, ""),
  };
}

/** 把区号与本地号码拼成带国际区号的手机号。 */
export function joinPhone(countryCode: string, phone: string): string {
  const number = phone.trim().replace(/[\s-]/g, "");
  if (!number) {
    return "";
  }
  const code = countryCode.trim() || "+86";
  return (code.startsWith("+") ? code : `+${code}`) + number;
}
