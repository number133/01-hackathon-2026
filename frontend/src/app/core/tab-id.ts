const TAB_ID_KEY = 'chat.tabId';

export function currentTabId(): string {
  try {
    const existing = sessionStorage.getItem(TAB_ID_KEY);
    if (existing) return existing;
    const fresh = crypto.randomUUID();
    sessionStorage.setItem(TAB_ID_KEY, fresh);
    return fresh;
  } catch {
    return 'ephemeral';
  }
}
