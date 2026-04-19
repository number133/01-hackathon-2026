import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { catchError, of, tap } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { ChatService } from '../chat/chat.service';

interface UnreadView {
  conversationId: string;
  count: number;
}

interface ConversationUnreadEvent {
  conversationId: string;
  authorId: string;
  lastSeq: number;
}

@Injectable({ providedIn: 'root' })
export class UnreadService {
  private readonly http = inject(HttpClient);
  private readonly chat = inject(ChatService);
  private readonly auth = inject(AuthService);

  private readonly countsMap = signal<Record<string, number>>({});
  private started = false;
  private readonly convUnsubs = new Map<string, () => void>();

  readonly total = computed(() =>
    Object.values(this.countsMap()).reduce((sum, n) => sum + n, 0),
  );

  start(): void {
    if (this.started) return;
    const me = this.auth.user();
    if (!me) return;
    this.started = true;
    this.refresh();
  }

  stop(): void {
    this.started = false;
    for (const unsub of this.convUnsubs.values()) unsub();
    this.convUnsubs.clear();
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
        // Subscribe to each conversation we're a member of so live
        // message posts bump the count without per-user fan-out.
        for (const v of list) this.ensureSubscribed(v.conversationId);
      });
  }

  countFor(conversationId: string | null | undefined): number {
    if (!conversationId) return 0;
    return this.countsMap()[conversationId] ?? 0;
  }

  markRead(conversationId: string, seq: number): void {
    // Optimistic: zero the local count immediately.
    this.countsMap.update((m) => ({ ...m, [conversationId]: 0 }));
    this.http
      .post(`/api/conversations/${conversationId}/read`, { seq })
      .pipe(catchError(() => of(null)))
      .subscribe();
  }

  /**
   * Refresh from the server, e.g., after a room join or dialog create, so new
   * conversations' unread topics get subscribed.
   */
  syncSubscriptions(): void {
    this.refresh();
  }

  private ensureSubscribed(conversationId: string): void {
    if (this.convUnsubs.has(conversationId)) return;
    const unsub = this.chat.subscribeTopic(
      `/topic/conversations/${conversationId}/unread`,
      (body) => this.applyEvent(body as ConversationUnreadEvent),
    );
    this.convUnsubs.set(conversationId, unsub);
  }

  private applyEvent(evt: ConversationUnreadEvent): void {
    const me = this.auth.user();
    if (!me || !evt?.conversationId) return;
    // Authors don't increment their own counter.
    if (evt.authorId && evt.authorId === me.id) return;
    this.countsMap.update((m) => ({
      ...m,
      [evt.conversationId]: (m[evt.conversationId] ?? 0) + 1,
    }));
  }
}
