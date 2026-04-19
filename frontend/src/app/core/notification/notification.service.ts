import { Injectable, signal } from '@angular/core';

export type NotificationKind = 'success' | 'info' | 'error';

export interface Notification {
  id: number;
  kind: NotificationKind;
  text: string;
}

const AUTO_DISMISS_MS = 5000;
const MAX_ITEMS = 5;

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly items = signal<Notification[]>([]);
  private nextId = 1;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  readonly list = this.items.asReadonly();

  success(text: string): void {
    this.push('success', text);
  }

  info(text: string): void {
    this.push('info', text);
  }

  error(text: string): void {
    this.push('error', text);
  }

  dismiss(id: number): void {
    const timer = this.timers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
    this.items.update((arr) => arr.filter((n) => n.id !== id));
  }

  private push(kind: NotificationKind, text: string): void {
    const id = this.nextId++;
    const item: Notification = { id, kind, text };
    this.items.update((arr) => {
      const next = [...arr, item];
      while (next.length > MAX_ITEMS) {
        const dropped = next.shift();
        if (dropped) this.clearTimer(dropped.id);
      }
      return next;
    });
    const timer = setTimeout(() => this.dismiss(id), AUTO_DISMISS_MS);
    this.timers.set(id, timer);
  }

  private clearTimer(id: number): void {
    const timer = this.timers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
  }
}
