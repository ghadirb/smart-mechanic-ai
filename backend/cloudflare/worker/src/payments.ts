import { creditsForProduct } from "./creditPolicy";
import type { Env } from "./types";

const MYKET_PACKAGE_NAME = "com.smartmechanic.ai";

export type MyketPurchase = { tokenId: string; developerPayload: string };

export function validatePurchaseBody(body: unknown): MyketPurchase | null {
  if (!body || typeof body !== "object") return null;
  const value = body as Record<string, unknown>;
  if (typeof value.tokenId !== "string" || value.tokenId.length < 8 || value.tokenId.length > 1024) return null;
  if (typeof value.developerPayload !== "string" || value.developerPayload.length < 16 || value.developerPayload.length > 256) return null;
  return { tokenId: value.tokenId, developerPayload: value.developerPayload };
}

export async function verifyMyketPurchase(env: Env, productId: string, tokenId: string): Promise<{ purchaseTime: number; developerPayload: string }> {
  if (!env.MYKET_ACCESS_TOKEN) throw new Error("MYKET_NOT_CONFIGURED");
  if (creditsForProduct(productId) == null) throw new Error("INVALID_PRODUCT");
  const url = `https://developer.myket.ir/api/partners/applications/${encodeURIComponent(MYKET_PACKAGE_NAME)}/purchases/products/${encodeURIComponent(productId)}/verify`;
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Access-Token": env.MYKET_ACCESS_TOKEN },
    body: JSON.stringify({ tokenId }),
  });
  const value: unknown = await response.json().catch(() => null);
  if (!response.ok || !value || typeof value !== "object") throw new Error("MYKET_VERIFICATION_FAILED");
  const purchase = value as Record<string, unknown>;
  if (purchase.purchaseState !== 0 || typeof purchase.developerPayload !== "string") throw new Error("MYKET_PURCHASE_NOT_VALID");
  return { developerPayload: purchase.developerPayload, purchaseTime: typeof purchase.purchaseTime === "number" ? purchase.purchaseTime : 0 };
}
