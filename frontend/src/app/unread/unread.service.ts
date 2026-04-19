import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { ChatService } from '../chat/chat.service';

interface UnreadView {
  conversationId: string;
  count: number;
}

interface UserScopedEvent {
  event: string;
  payload: Record<string, unknown>;
}

@Injectable({ providedIn: 'root' })
export class UnreadService {
  private readonly http = inject(HttpClient);
  private readonly chat = inject(ChatService);
  private readonly auth = inject(AuthService);

  private readonly countsMap = signal<Record<string, number>>({});
  private started = false;
  private unsubscribe: (() => void) | null = null;

  readonly total = computed(() =>
    Object.values(this.countsMap()).reduce((sum, n) => sum + n, 0),
  );

  start(): void {
    if (this.started) return;
    const me = this.auth.user();
    if (!me) return;
    this.started = true;
    this.refresh();
    this.unsubscribe = this.chat.subscribeTopic(`/topic/users/${me.id}`, (body) => {
      const evt = body as UserScopedEvent;
      if (evt.event !== 'unread.updated') return;
      const payload = evt.payload as { conversationId: string; count: number };
      this.countsMap.update((m) => ({ ...m, [payload.conversationId]: payload.count }));
    });
  }

  stop(): void {
    this.started = false;
    if (this.unsubscribe) {
      this.unsubscribe();
      this.unsubscribe = null;
    }
    this.countsMap.set({});
  }

  refresh(): void {
    this.http
      .get<UnreadView[]>('/api/unread')
      .pipe(catchError(() => of<UnreadView[]>([])))
      .subscribe((list) => {
        const next: Record<string, number> = {};
        for (const v of list) next[v.conversationId] = v.count;
        this.countsMap.set(next);
      });
  }

  countFor(conversationId: string | null | undefined): number {
    if (!conversationId) return 0;
    return this.countsMap()[conversationId] ?? 0;
  }

  markRead(conversationId: string, seq: number): void {
    // Optimistic: zero the local count immediately. Server will send a
    // confirming unread.updated via /topic/users/{me}.
    this.countsMap.update((m) => ({ ...m, [conversationId]: 0 }));
    this.http
      .post(`/api/conversations/${conversationId}/read`, { seq })
      .pipe(catchError(() => of(null)))
      .subscribe();
  }
}
