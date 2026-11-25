import { JSEncrypt } from "jsencrypt";
import { ApiError, request, type RequestOptions } from "../api/client";

/** 后端 /api/auth/public-key 的返回结构。 */
interface FormKey {
  algorithm: string;
  publicKey: string;
  chunkSize: number;
}

let cachedKey: Promise<FormKey> | null = null;

/** 公钥在页面生命周期内复用；失败时清空缓存，下次提交重新获取。 */
function loadFormKey(): Promise<FormKey> {
  if (!cachedKey) {
    cachedKey = request<FormKey>("/auth/public-key", { handleUnauthorized: false })
      .then((key) => {
        if (!key?.publicKey || !key.chunkSize) {
          throw new Error("表单加密公钥不可用");
        }
        return key;
      })
      .catch((error) => {
        cachedKey = null;
        throw error;
      });
  }
  return cachedKey;
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  // 分片拼接，避免超大数组展开导致调用栈溢出。
  for (let index = 0; index < bytes.length; index += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(index, index + 0x8000));
  }
  return btoa(binary);
}

/**
 * 用后端公钥把表单 JSON 分块做 RSA 加密。
 *
 * 每块先取原始 UTF-8 字节，再 Base64 成 ASCII 字符串交给 JSEncrypt；
 * 后端解密后再 Base64 解码并拼回完整 JSON。分块是为了绕过 RSA
 * 单块明文长度限制（2048 位密钥约 245 字节）。
 */
export async function encryptForm(values: Record<string, unknown>): Promise<string[]> {
  const { publicKey, chunkSize } = await loadFormKey();
  const encryptor = new JSEncrypt();
  encryptor.setPublicKey(publicKey);

  const bytes = new TextEncoder().encode(JSON.stringify(values));
  const size = Math.max(32, chunkSize);
  const chunks: string[] = [];
  for (let offset = 0; offset < bytes.length; offset += size) {
    const block = bytes.subarray(offset, Math.min(offset + size, bytes.length));
    const encrypted = encryptor.encrypt(bytesToBase64(block));
    if (!encrypted) {
      // 公钥可能已变化（例如服务端重新生成密钥），清空缓存让用户重试。
      cachedKey = null;
      throw new Error("表单加密失败，请刷新页面后重试");
    }
    chunks.push(encrypted);
  }
  return chunks;
}

/**
 * 加密提交表单。服务端轮换 RSA 密钥后会返回 invalid_encrypted_form，
 * 此时清空公钥缓存并用新公钥重试一次，避免用户必须刷新页面。
 */
export async function encryptedRequest<T>(
  path: string,
  values: Record<string, unknown>,
  options: RequestOptions = {},
): Promise<T> {
  const send = async () => {
    const chunks = await encryptForm(values);
    return request<T>(path, {
      method: "POST",
      ...options,
      data: { chunks },
    });
  };
  try {
    return await send();
  } catch (error) {
    const retryable = error instanceof ApiError
      && error.status === 400
      && (error.message.includes("解密失败") || error.message.includes("表单"));
    if (!retryable) {
      throw error;
    }
    cachedKey = null;
    return send();
  }
}
