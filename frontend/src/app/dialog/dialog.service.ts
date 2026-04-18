import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { ChatService, MessageView } from '../chat/chat.service';

export interface DialogView {
  id: string;
  counterpartId: string;
  counterpartUsername: string;
  frozen: boolean;
  lastMessageAt: string | null;
}

interface DialogState {
  messages: MessageView[];
  highestSeq: number;
  loading: boolean;
  endReached: boolean;
  frozen: boolean;
}

const EMPTY_STATE: DialogState = {
  messages: [],
  highestSeq: 0,
  loading: false,
  endReached: false,
  frozen: false,
};

interface WsEvent {
  event: 'message.created' | 'message.edited' | 'message.deleted';
  conversationId: string;
  roomId: string | null;
  seq: number;
  message: MessageView;
}

@Injectable({ providedIn: 'root' })
export class DialogService {
  private readonly http = inject(HttpClient);
  private readonly chat = inject(ChatService);

  private readonly states = signal<Record<string, DialogState>>({});
  private readonly subs = new Map<string, () => void>();

  state(id: string): DialogState {
    return this.states()[id] ?? EMPTY_STATE;
  }

  list(): Observable<DialogView[]> {
    return this.http.get<DialogView[]>('/api/dialogs');
  }

  open(userId: string): Observable<DialogView> {
    return this.http.post<DialogView>('/api/dialogs', { userId });
  }

  get(id: string): Observable<DialogView> {
    return this.http.get<DialogView>(`/api/dialogs/${id}`);
  }

  loadInitial(id: string): Observable<MessageView[]> {
    this.states.update((s) => ({
      ...s,
      [id]: { ...(s[id] ?? EMPTY_STATE), loading: true },
    }));
    return this.http
      .get<{ items: MessageView[]; pageSize: number }>(`/api/dialogs/${id}/messages`)
      .pipe(
        tap((res) => {
          const messages = [...res.items].reverse();
          const highestSeq = messages.length ? messages[messages.length - 1].seq : 0;
          this.states.update((s) => ({
            ...s,
            [id]: {
              ...(s[id] ?? EMPTY_STATE),
              messages,
              highestSeq,
              loading: false,
              endReached: res.items.length < 50,
            },
          }));
        }),
      ) as unknown as Observable<MessageView[]>;
  }

  subscribeDialog(id: string): void {
    if (this.subs.has(id)) return;
    const unsub = this.chat.subscribeTopic(`/topic/dialogs/${id}`, (body) => {
      const evt = body as WsEvent;
      this.apply(id, evt);
    });
    this.subs.set(id, unsub);
  }

  unsubscribeDialog(id: string): void {
    const unsub = this.subs.get(id);
    if (unsub) {
      unsub();
      this.subs.delete(id);
    }
  }

  post(id: string, text: string, replyToId: string | null): Observable<MessageView> {
    return this.http.post<MessageView>(`/api/dialogs/${id}/messages`, { text, replyToId });
  }

  setFrozen(id: string, frozen: boolean): void {
    this.states.update((s) => ({
      ...s,
      [id]: { ...(s[id] ?? EMPTY_STATE), frozen },
    }));
  }

  private apply(id: string, evt: WsEvent): void {
    const current = this.state(id);
    if (evt.event === 'message.created') {
      if (evt.seq <= current.highestSeq) return;
      this.states.update((s) => ({
        ...s,
        [id]: {
          ...current,
          messages: [...current.messages, evt.message],
          highestSeq: evt.seq,
        },
      }));
      return;
    }
    this.states.update((s) => {
      const st = s[id] ?? EMPTY_STATE;
      return {
        ...s,
        [id]: {
          ...st,
          messages: st.messages.map((m) => (m.id === evt.message.id ? evt.message : m)),
        },
      };
    });
  }
}
