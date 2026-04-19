import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, of, take, tap } from 'rxjs';

import { ChatService } from '../chat/chat.service';
import { currentTabId } from '../core/tab-id';

export type PresenceStatus = 'online' | 'afk' | 'offline';

interface PresenceEvent {
  userId: string;
  status: PresenceStatus;
  at: number;
}

interface PresenceConfigDto {
  pingIntervalMs: number;
}

const DEFAULT_PING_INTERVAL_MS = 2000;
const ACTIVITY_EVENTS: readonly (keyof WindowEventMap)[] = [
  'mousemove',
  'keydown',
  'focus',
  'touchstart',
];

@Injectable({ providedIn: 'root' })
export class PresenceService {
  private readonly http = inject(HttpClient);
  private readonly chat = inject(ChatService);

  private readonly statuses = signal<Record<string, PresenceStatus>>({});
  private readonly unsubscribers = new Map<string, () => void>();
  private readonly watchCount = new Map<string, number>();

  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private activitySeen = false;
  private pingIntervalMs = DEFAULT_PING_INTERVAL_MS;

  start(): void {
    if (this.pingTimer !== null) return;
    this.http
      .get<PresenceConfigDto>('/api/presence/config')
      .pipe(
        catchError(() => of<PresenceConfigDto>({ pingIntervalMs: DEFAULT_PING_INTERVAL_MS })),
      )
      .subscribe((cfg) => {
        this.pingIntervalMs = cfg.pingIntervalMs > 0 ? cfg.pingIntervalMs : DEFAULT_PING_INTERVAL_MS;
        this.installActivityListeners();
        this.pingTimer = setInterval(() => this.maybePing(), this.pingIntervalMs);
        // Emit one immediate ping so the user appears online as soon as the app boots.
        this.activitySeen = true;
        this.maybePing();
      });
  }

  status(userId: string): PresenceStatus {
    return this.statuses()[userId] ?? 'offline';
  }

  watch(userIds: string[]): void {
    const novel: string[] = [];
    for (const id of userIds) {
      const n = (this.watchCount.get(id) ?? 0) + 1;
      this.watchCount.set(id, n);
      if (n === 1) {
        novel.push(id);
        const unsub = this.chat.subscribeTopic(`/topic/presence/${id}`, (body) =>
          this.applyEvent(body as PresenceEvent),
        );
        this.unsubscribers.set(id, unsub);
      }
    }
    if (novel.length > 0) {
      this.hydrate(novel);
    }
  }

  unwatch(userIds: string[]): void {
    for (const id of userIds) {
      const n = this.watchCount.get(id);
      if (!n) continue;
      if (n === 1) {
        this.watchCount.delete(id);
        const unsub = this.unsubscribers.get(id);
        if (unsub) {
          unsub();
          this.unsubscribers.delete(id);
        }
      } else {
        this.watchCount.set(id, n - 1);
      }
    }
  }

  private hydrate(userIds: string[]): void {
    if (userIds.length === 0) return;
    const qs = userIds.map(encodeURIComponent).join(',');
    this.http
      .get<{ userId: string; status: PresenceStatus }[]>(`/api/presence?userIds=${qs}`)
      .pipe(take(1), catchError(() => of([])))
      .subscribe((list) => {
        if (list.length === 0) return;
        this.statuses.update((s) => {
          const next = { ...s };
          for (const v of list) next[v.userId] = v.status;
          return next;
        });
      });
  }

  private applyEvent(evt: PresenceEvent): void {
    this.statuses.update((s) => ({ ...s, [evt.userId]: evt.status }));
  }

  private maybePing(): void {
    if (!this.activitySeen) return;
    this.activitySeen = false;
    const tabId = this.tabId();
    this.http
      .post('/api/presence/ping', { tabId })
      .pipe(
        take(1),
        catchError(() => of(null)),
        tap(() => undefined),
      )
      .subscribe();
  }

  private installActivityListeners(): void {
    const mark = () => {
      this.activitySeen = true;
    };
    for (const ev of ACTIVITY_EVENTS) {
      window.addEventListener(ev, mark, { passive: true });
    }
    window.addEventListener('focus', () => this.refreshOnFocus(), { passive: true });
  }

  private refreshOnFocus(): void {
    const ids = Array.from(this.watchCount.keys());
    if (ids.length > 0) this.hydrate(ids);
  }

  tabId(): string {
    return currentTabId();
  }
}
