/**
 * Minimal {{placeholder}} renderer for pack-authored prompt templates (spec §11). Deliberately
 * dumb — no logic, no nesting — so templates stay data, not code. Unknown placeholders throw
 * rather than silently rendering "{{x}}" into a prompt the model will see.
 */
export function renderTemplate(template: string, vars: Record<string, string>): string {
  return template.replace(/\{\{\s*([a-zA-Z0-9_.]+)\s*\}\}/g, (_, name: string) => {
    const value = vars[name];
    if (value === undefined) {
      throw new Error(`prompt template references unknown placeholder {{${name}}}`);
    }
    return value;
  });
}
