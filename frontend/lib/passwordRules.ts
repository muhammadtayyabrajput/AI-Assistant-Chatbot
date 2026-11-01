export const PASSWORD_RULES = [
  { label: "Starts with a capital letter", test: (p: string) => /^[A-Z]/.test(p) },
  { label: "At least one number", test: (p: string) => /\d/.test(p) },
  { label: "At least one special character (!@#$…)", test: (p: string) => /[^A-Za-z0-9]/.test(p) },
  { label: "At least 6 characters", test: (p: string) => p.length >= 6 },
];

export const MAX_PASSWORD_LENGTH = 72;

export const passwordValid = (p: string) => p.length <= MAX_PASSWORD_LENGTH && PASSWORD_RULES.every((r) => r.test(p));
