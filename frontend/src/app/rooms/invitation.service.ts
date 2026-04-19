import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export type InvitationStatus = 'pending' | 'accepted' | 'declined' | 'revoked';

export interface InvitationView {
  id: string;
  roomId: string;
  roomName: string;
  inviterId: string | null;
  inviterUsername: string | null;
  inviteeId: string;
  inviteeUsername: string;
  message: string | null;
  status: InvitationStatus;
  createdAt: string;
  resolvedAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class InvitationService {
  private readonly http = inject(HttpClient);

  readonly pendingCount = signal(0);

  listMine(): Observable<InvitationView[]> {
    return this.http
      .get<InvitationView[]>('/api/invitations')
      .pipe(tap((list) => this.pendingCount.set(list.length)));
  }

  listForRoom(roomId: string): Observable<InvitationView[]> {
    return this.http.get<InvitationView[]>(`/api/rooms/${roomId}/invitations`);
  }

  invite(roomId: string, username: string, message?: string): Observable<InvitationView> {
    return this.http.post<InvitationView>(`/api/rooms/${roomId}/invitations`, {
      username,
      message: message ?? null,
    });
  }

  accept(id: string): Observable<void> {
    return this.http
      .post<void>(`/api/invitations/${id}/accept`, {})
      .pipe(tap(() => this.pendingCount.update((n) => Math.max(0, n - 1))));
  }

  decline(id: string): Observable<void> {
    return this.http
      .post<void>(`/api/invitations/${id}/decline`, {})
      .pipe(tap(() => this.pendingCount.update((n) => Math.max(0, n - 1))));
  }

  revoke(id: string): Observable<void> {
    return this.http.delete<void>(`/api/invitations/${id}`);
  }

  refreshCount(): void {
    this.listMine().subscribe({ error: () => this.pendingCount.set(0) });
  }
}
