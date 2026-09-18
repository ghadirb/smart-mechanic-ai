export type DiagnosisAction = "text" | "image" | "audio" | "video";

export interface Env {
  DB: D1Database;
  // این دو مقدار فقط با «wrangler secret put» تنظیم می‌شوند، هرگز در کد یا
  // wrangler.toml قرار نمی‌گیرند -- رجوع کنید به README همین پوشه.
  AVALAI_API_KEY: string;
  AVALAI_MODEL: string;
  MYKET_ACCESS_TOKEN?: string;
}

export interface DiagnoseRequestBody {
  action: DiagnosisAction;
  prompt: string;
  imageBase64?: string;
  imageMimeType?: string;
  audioBase64?: string;
  audioMimeType?: string;
  videoBase64?: string;
  videoMimeType?: string;
}

export interface UserRow {
  user_id: string;
  token_hash: string;
  credits: number;
  device_hash: string | null;
  last_request_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface TransactionRow {
  id: string;
  user_id: string;
  amount: number;
  type: string;
  request_id: string;
  description: string | null;
  response_raw: string | null;
  created_at: string;
}

export interface TransactionHistoryItem {
  id: string;
  type: string;
  amount: number;
  description: string | null;
  created_at: string;
  request_id: string;
}
