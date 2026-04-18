import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Observable, tap } from 'rxjs';

export interface MessageReplyRef {
  id: string;
  seq: number;
  authorUsername: string;
  bodyPreview: string | null;
}

export interface MessageView {
  id: string;
  conversationId: string;
  roomId: string;
  seq: number;
  authorId: string | null;
  authorUsername: string;
  body: string | null;
  replyTo: MessageReplyRef | null;
  createdAt: string;
  editedAt: string | null;
  deletedAt: string | null;
}

export interface WsEvent {
  event: 'message.created' | 'message.edited' | 'message.deleted';
  conversationId: string;
  roomId: string;
  seq: number;
  message: MessageView;
}

interface RoomState {
  messages: MessageView[];
  highestSeq: number;
  loading: boolean;
  endReached: boolean;
}

const EMPTY_STATE: RoomState = {
  messages: [],
  highestSeq: 0,
  loading: false,
  endReached: false,
};

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly rooms = signal<Record<string, RoomState>>({});

  private client: Client | null = null;
  private connected = false;
  private readonly pendingSubscriptions = new Map<string, number>();
  private readonly subscriptions = new Map<string, StompSubscription>();

  state(roomId: string): RoomState {
    return this.rooms()[roomId] ?? EMPTY_STATE;
  }

  connect(): void {
    if (this.client) return;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${protocol}//${window.location.host}/ws`;
    this.client = new Client({
      brokerURL: url,
      reconnectDelay: 2000,
      onConnect: () => {
        this.connected = true;
        for (const roomId of this.pendingSubscriptions.keys()) {
          this.wireSubscription(roomId);
        }
      },
      onDisconnect: () => {
        this.connected = false;
        this.subscriptions.clear();
      },
      onStompError: () => {
        this.connected = false;
        this.subscriptions.clear();
      },
      onWebSocketClose: () => {
        this.connected = false;
        this.subscriptions.clear();
      },
    });
    this.client.activate();
  }

  subscribeRoom(roomId: string): void {
    const count = this.pendingSubscriptions.get(roomId) ?? 0;
    this.pendingSubscriptions.set(roomId, count + 1);
    if (count === 0) {
      if (this.connected) {
        this.wireSubscription(roomId);
      }
    }
  }

  unsubscribeRoom(roomId: string): void {
    const count = this.pendingSubscriptions.get(roomId);
    if (!count) return;
    if (count === 1) {
      this.pendingSubscriptions.delete(roomId);
      const sub = this.subscriptions.get(roomId);
      if (sub) {
        sub.unsubscribe();
        this.subscriptions.delete(roomId);
      }
    } else {
      this.pendingSubscriptions.set(roomId, count - 1);
    }
  }

  loadInitial(roomId: string): Observable<MessageView[]> {
    this.rooms.update((s) => ({
      ...s,
      [roomId]: { ...(s[roomId] ?? EMPTY_STATE), loading: true },
    }));
    return this.http
      .get<{ items: MessageView[]; pageSize: number }>(`/api/rooms/${roomId}/messages`)
      .pipe(
        tap((res) => {
          const messages = [...res.items].reverse();
          const highestSeq = messages.length ? messages[messages.length - 1].seq : 0;
          this.rooms.update((s) => ({
            ...s,
            [roomId]: {
              messages,
              highestSeq,
              loading: false,
              endReached: res.items.length < 50,
            },
          }));
        }),
      ) as unknown as Observable<MessageView[]>;
  }

  loadMore(roomId: string): Observable<MessageView[]> {
    const current = this.state(roomId);
    if (current.endReached || current.messages.length === 0) {
      return new Observable((sub) => { sub.next([]); sub.complete(); });
    }
    const beforeSeq = current.messages[0].seq;
    this.rooms.update((s) => ({ ...s, [roomId]: { ...current, loading: true } }));
    return this.http
      .get<{ items: MessageView[]; pageSize: number }>(
        `/api/rooms/${roomId}/messages?beforeSeq=${beforeSeq}`,
      )
      .pipe(
        tap((res) => {
          const older = [...res.items].reverse();
          this.rooms.update((s) => {
            const st = s[roomId] ?? EMPTY_STATE;
            return {
              ...s,
              [roomId]: {
                ...st,
                messages: [...older, ...st.messages],
                loading: false,
                endReached: res.items.length < 50,
              },
            };
          });
        }),
      ) as unknown as Observable<MessageView[]>;
  }

  post(roomId: string, text: string, replyToId: string | null): Observable<MessageView> {
    return this.http
      .post<MessageView>(`/api/rooms/${roomId}/messages`, { text, replyToId });
  }

  edit(messageId: string, text: string): Observable<MessageView> {
    return this.http.patch<MessageView>(`/api/messages/${messageId}`, { text });
  }

  delete(messageId: string): Observable<void> {
    return this.http.delete<void>(`/api/messages/${messageId}`);
  }

  private wireSubscription(roomId: string): void {
    if (!this.client || !this.connected) return;
    if (this.subscriptions.has(roomId)) return;
    const sub = this.client.subscribe(`/topic/rooms/${roomId}`, (frame: IMessage) => {
      try {
        const event = JSON.parse(frame.body) as WsEvent;
        this.applyEvent(roomId, event);
      } catch {
        // ignore malformed frames
      }
    });
    this.subscriptions.set(roomId, sub);
  }

  private applyEvent(roomId: string, event: WsEvent): void {
    const current = this.state(roomId);
    if (event.event === 'message.created') {
      if (event.seq <= current.highestSeq) return;
      if (event.seq > current.highestSeq + 1 && current.highestSeq > 0) {
        // Gap detected — refetch tail.
        this.loadInitial(roomId).subscribe();
        return;
      }
      this.rooms.update((s) => ({
        ...s,
        [roomId]: {
          ...current,
          messages: [...current.messages, event.message],
          highestSeq: event.seq,
        },
      }));
      return;
    }
    // edit / delete — patch in place
    this.rooms.update((s) => {
      const st = s[roomId] ?? EMPTY_STATE;
      return {
        ...s,
        [roomId]: {
          ...st,
          messages: st.messages.map((m) => (m.id === event.message.id ? event.message : m)),
        },
      };
    });
  }
}
