import { describe, expect, it } from "vitest";
import { PASSWORD_RULES, passwordValid } from "./passwordRules";

describe("passwordValid", () => {
  it.each(["Abcde1!x", "Zebra#2024", "Q1_aaaa", "Password1!"])("accepts %s", (p) => {
    expect(passwordValid(p)).toBe(true);
  });

  it.each([
    ["abcde1!x", "no leading capital"],
    ["1Abcde!x", "starts with a digit"],
    ["!Abcde1x", "starts with a symbol"],
    ["Abcdefg!", "no number"],
    ["Abcdefg1", "no special character"],
    ["A1!", "too short"],
    ["", "empty"],
  ])("rejects %s (%s)", (p) => {
    expect(passwordValid(p)).toBe(false);
  });

  it("rejects passwords longer than bcrypt can use", () => {
    expect(passwordValid("A1!" + "a".repeat(80))).toBe(false);
  });

  it("reports each rule independently so the checklist can tick them one by one", () => {
    const results = PASSWORD_RULES.map((r) => r.test("abc"));
    expect(results).toEqual([false, false, false, false]);
    expect(PASSWORD_RULES.map((r) => r.test("Abc1!x"))).toEqual([true, true, true, true]);
  });
});
