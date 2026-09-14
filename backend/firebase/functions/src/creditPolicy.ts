export type DiagnosisAction = "text" | "image" | "audio" | "video";

export const CREDIT_COST: Record<DiagnosisAction, number> = {
  text: 1,
  image: 3,
  audio: 5,
  video: 12,
};

export const CREDIT_PACKAGES = [
  { productId: "credit_20", credits: 20 },
  { productId: "credit_50", credits: 50 },
  { productId: "credit_150", credits: 150 },
] as const;
