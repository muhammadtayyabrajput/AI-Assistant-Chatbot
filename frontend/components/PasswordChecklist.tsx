import { PASSWORD_RULES } from "@/lib/passwordRules";

export default function PasswordChecklist({ password }: { password: string }) {
  return (
    <ul className="grid grid-cols-1 gap-1 px-1 pt-1 text-[13px]">
      {PASSWORD_RULES.map((r) => {
        const ok = r.test(password);
        return (
          <li key={r.label} className={`flex items-center gap-2 transition ${ok ? "text-forest-2" : "text-muted"}`}>
            <span
              className={`flex h-4 w-4 items-center justify-center rounded-full text-[10px] ${
                ok ? "bg-lime text-forest" : "border border-line"
              }`}
            >
              {ok && "✓"}
            </span>
            {r.label}
          </li>
        );
      })}
    </ul>
  );
}
