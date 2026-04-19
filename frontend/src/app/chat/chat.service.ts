import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Observable, tap } from 'rxjs';

import { NotificationService } from '../core/notification/notification.service';
import { HistoryResponse, MESSAGE_PAGE_SIZE } from './pagination';

export interface MessageReplyRef {
  id: string;
  seq: number;
  authorUsername: string;
  bodyPreview: string | null;
}

export interface AttachmentRef {
  id: string;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
  comment: string | null;
  isImage: boolean;
}

export interface MessageView {
  id: string;
  conversationId: string;
  roomId: string | null;
  seq: number;
  authorId: string | null;
  authorUsername: string;
  body: string | null;
  replyTo: MessageReplyRef | null;
  attachments: AttachmentRef[];
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
  private readonly notifications = inject(NotificationService);
  private readonly rooms = signal<Record<string, RoomState>>({});

  private client: Client | null = null;
  private connected = false;
  private hasConnectedOnce = false;
  private readonly pendingSubscriptions = new Map<string, number>();
  private readonly subscriptions = new Map<string, StompSubscription>();
  private readonly topicHandlers = new Map<string, Set<(body: unknown) => void>>();
  private readonly topicSubs = new Map<string, StompSubscription>();

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
        const wasDisconnected = this.hasConnectedOnce && !this.connected;
        this.connected = true;
        if (wasDisconnected) {
          this.notifications.success('Reconnected');
        }
        this.hasConnectedOnce = true;
        for (const roomId of this.pendingSubscriptions.keys()) {
          this.wireSubscription(roomId);
        }
        for (const destination of this.topicHandlers.keys()) {
          this.wireTopic(destination);
        }
      },
      onDisconnect: () => {
        this.handleConnectionLoss();
      },
      onStompError: () => {
        this.handleConnectionLoss();
      },
      onWebSocketClose: () => {
        this.handleConnectionLoss();
      },
    });
    this.client.activate();
  }

  private handleConnectionLoss(): void {
    if (this.connected && this.hasConnectedOnce) {
      this.notifications.info('Reconnecting…');
    }
    this.connected = false;
    this.subscriptions.clear();
    this.topicSubs.clear();
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
      .get<HistoryResponse<MessageView>>(
        `/api/rooms/${roomId}/messages?limit=${MESSAGE_PAGE_SIZE}`,
      )
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
              endReached: !res.hasMore,
            },
          }));
        }),
      ) as unknown as Observable<MessageView[]>;
  }

  loadMore(roomId: string): Observable<MessageView[]> {
    const current = this.state(roomId);
    if (current.endReached || current.messages.length === 0 || current.loading) {
      return new Observable((sub) => { sub.next([]); sub.complete(); });
    }
    const beforeSeq = current.messages[0].seq;
    this.rooms.update((s) => ({ ...s, [roomId]: { ...current, loading: true } }));
    return this.http
      .get<HistoryResponse<MessageView>>(
        `/api/rooms/${roomId}/messages?beforeSeq=${beforeSeq}&limit=${MESSAGE_PAGE_SIZE}`,
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
                endReached: !res.hasMore,
              },
            };
          });
        }),
      ) as unknown as Observable<MessageView[]>;
  }

  post(
    roomId: string,
    text: string,
    replyToId: string | null,
    attachmentIds: string[] = [],
  ): Observable<MessageView> {
    return this.http.post<MessageView>(`/api/rooms/${roomId}/messages`, {
      text,
      replyToId,
      attachmentIds,
    });
  }

  edit(messageId: string, text: string): Observable<MessageView> {
    return this.http.patch<MessageView>(`/api/messages/${messageId}`, { text });
  }

  delete(messageId: string): Observable<void> {
    return this.http.delete<void>(`/api/messages/${messageId}`);
  }

  subscribeTopic(destination: string, handler: (body: unknown) => void): () => void {
    let set = this.topicHandlers.get(destination);
    if (!set) {
      set = new Set();
      this.topicHandlers.set(destination, set);
    }
    set.add(handler);
    this.wireTopic(destination);
    return () => this.unsubscribeTopic(destination, handler);
  }

  private unsubscribeTopic(destination: string, handler: (body: unknown) => void): void {
    const set = this.topicHandlers.get(destination);
    if (!set) return;
    set.delete(handler);
    if (set.size === 0) {
      this.topicHandlers.delete(destination);
      const sub = this.topicSubs.get(destination);
      if (sub) {
        sub.unsubscribe();
        this.topicSubs.delete(destination);
      }
    }
  }

  private wireTopic(destination: string): void {
    if (!this.client || !this.connected) return;
    if (this.topicSubs.has(destination)) return;
    const sub = this.client.subscribe(destination, (frame: IMessage) => {
      try {
        const body = JSON.parse(frame.body);
        const handlers = this.topicHandlers.get(destination);
        handlers?.forEach((h) => h(body));
      } catch {
        // ignore malformed frames
      }
    });
    this.topicSubs.set(destination, sub);
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
