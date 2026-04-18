import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { ChatService } from '../chat/chat.service';

export type FriendRequestStatus =
  | 'pending'
  | 'accepted'
  | 'declined'
  | 'revoked'
  | 'superseded';

export interface FriendRequestView {
  id: string;
  requester: { id: string; username: string };
  recipient: { id: string; username: string };
  message: string | null;
  status: FriendRequestStatus;
  createdAt: string;
  resolvedAt: string | null;
}

export interface FriendView {
  userId: string;
  username: string;
  establishedAt: string;
}

export interface UserBanView {
  userId: string;
  username: string;
  createdAt: string;
}

interface UserScopedEvent {
  event: string;
  payload: Record<string, unknown>;
}

@Injectable({ providedIn: 'root' })
export class FriendService {
  private readonly http = inject(HttpClient);
  private readonly chat = inject(ChatService);
  private readonly auth = inject(AuthService);

  private readonly friendsSig = signal<FriendView[]>([]);
  private readonly incomingSig = signal<FriendRequestView[]>([]);
  private readonly outgoingSig = signal<FriendRequestView[]>([]);
  private readonly bansSig = signal<UserBanView[]>([]);
  private started = false;
  private unsubscribe: (() => void) | null = null;

  readonly friends = this.friendsSig.asReadonly();
  readonly incoming = this.incomingSig.asReadonly();
  readonly outgoing = this.outgoingSig.asReadonly();
  readonly bans = this.bansSig.asReadonly();
  readonly incomingPendingCount = computed(
    () => this.incomingSig().filter((r) => r.status === 'pending').length,
  );

  start(): void {
    if (this.started) return;
    const me = this.auth.user();
    if (!me) return;
    this.started = true;
    this.refreshAll();
    this.unsubscribe = this.chat.subscribeTopic(`/topic/users/${me.id}`, (body) =>
      this.applyEvent(body as UserScopedEvent),
    );
  }

  stop(): void {
    this.started = false;
    if (this.unsubscribe) {
      this.unsubscribe();
      this.unsubscribe = null;
    }
    this.friendsSig.set([]);
    this.incomingSig.set([]);
    this.outgoingSig.set([]);
    this.bansSig.set([]);
  }

  refreshAll(): void {
    this.http.get<FriendView[]>('/api/friends').subscribe((xs) => this.friendsSig.set(xs));
    this.http
      .get<FriendRequestView[]>('/api/friend-requests?direction=incoming')
      .subscribe((xs) => this.incomingSig.set(xs));
    this.http
      .get<FriendRequestView[]>('/api/friend-requests?direction=outgoing')
      .subscribe((xs) => this.outgoingSig.set(xs));
    this.http.get<UserBanView[]>('/api/user-bans').subscribe((xs) => this.bansSig.set(xs));
  }

  send(username: string, message: string): Observable<FriendRequestView> {
    const payload = message.trim().length > 0 ? { username, message } : { username };
    return this.http
      .post<FriendRequestView>('/api/friend-requests', payload)
      .pipe(tap(() => this.refreshAll()));
  }

  accept(id: string): Observable<void> {
    return this.http
      .post<void>(`/api/friend-requests/${id}/accept`, {})
      .pipe(tap(() => this.refreshAll()));
  }

  decline(id: string): Observable<void> {
    return this.http
      .post<void>(`/api/friend-requests/${id}/decline`, {})
      .pipe(tap(() => this.refreshAll()));
  }

  revoke(id: string): Observable<void> {
    return this.http
      .delete<void>(`/api/friend-requests/${id}`)
      .pipe(tap(() => this.refreshAll()));
  }

  unfriend(userId: string): Observable<void> {
    return this.http
      .delete<void>(`/api/friends/${userId}`)
      .pipe(tap(() => this.refreshAll()));
  }

  ban(userId: string): Observable<void> {
    return this.http
      .post<void>(`/api/user-bans/${userId}`, {})
      .pipe(tap(() => this.refreshAll()));
  }

  unban(userId: string): Observable<void> {
    return this.http
      .delete<void>(`/api/user-bans/${userId}`)
      .pipe(tap(() => this.refreshAll()));
  }

  private applyEvent(evt: UserScopedEvent): void {
    // Naive: any event that could affect friend state triggers a refetch.
    // Cheap at hackathon scale and avoids every client having to implement
    // delta-merging for every event type.
    if (
      evt.event === 'friend-request.created' ||
      evt.event === 'friend-request.resolved' ||
      evt.event === 'friend.added' ||
      evt.event === 'friend.removed' ||
      evt.event === 'user-ban.added' ||
      evt.event === 'user-ban.removed'
    ) {
      this.refreshAll();
    }
  }
}
