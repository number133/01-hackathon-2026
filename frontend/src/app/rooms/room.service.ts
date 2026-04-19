import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type RoomVisibility = 'public' | 'private';
export type RoomRole = 'owner' | 'admin' | 'member';

export interface RoomView {
  id: string;
  conversationId: string;
  name: string;
  description: string;
  visibility: RoomVisibility;
  ownerId: string;
  ownerUsername: string;
  memberCount: number;
  myRole: RoomRole | null;
  createdAt: string;
}

export interface RoomMemberView {
  userId: string;
  username: string;
  role: RoomRole;
  joinedAt: string;
}

export interface RoomBanView {
  userId: string;
  username: string;
  bannedById: string | null;
  bannedByUsername: string | null;
  reason: string | null;
  bannedAt: string;
}

export interface CreateRoomPayload {
  name: string;
  description: string;
  visibility: RoomVisibility;
}

export interface UpdateRoomPayload {
  name?: string;
  description?: string;
  visibility?: RoomVisibility;
}

@Injectable({ providedIn: 'root' })
export class RoomService {
  private readonly http = inject(HttpClient);

  listCatalog(q?: string): Observable<RoomView[]> {
    const qs = q && q.trim().length > 0 ? `?q=${encodeURIComponent(q.trim())}` : '';
    return this.http.get<RoomView[]>(`/api/rooms${qs}`);
  }

  listMine(): Observable<RoomView[]> {
    return this.http.get<RoomView[]>('/api/rooms/mine');
  }

  get(id: string): Observable<RoomView> {
    return this.http.get<RoomView>(`/api/rooms/${id}`);
  }

  create(payload: CreateRoomPayload): Observable<RoomView> {
    return this.http.post<RoomView>('/api/rooms', payload);
  }

  update(id: string, patch: UpdateRoomPayload): Observable<RoomView> {
    return this.http.patch<RoomView>(`/api/rooms/${id}`, patch);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/rooms/${id}`);
  }

  join(id: string): Observable<void> {
    return this.http.post<void>(`/api/rooms/${id}/join`, {});
  }

  leave(id: string): Observable<void> {
    return this.http.delete<void>(`/api/rooms/${id}/members/me`);
  }

  listMembers(id: string): Observable<RoomMemberView[]> {
    return this.http.get<RoomMemberView[]>(`/api/rooms/${id}/members`);
  }

  remove(roomId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`/api/rooms/${roomId}/members/${userId}`);
  }

  ban(roomId: string, userId: string, reason: string | null): Observable<RoomBanView> {
    return this.http.post<RoomBanView>(`/api/rooms/${roomId}/bans`, { userId, reason });
  }

  unban(roomId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`/api/rooms/${roomId}/bans/${userId}`);
  }

  listBans(roomId: string): Observable<RoomBanView[]> {
    return this.http.get<RoomBanView[]>(`/api/rooms/${roomId}/bans`);
  }

  promote(roomId: string, userId: string): Observable<void> {
    return this.http.put<void>(`/api/rooms/${roomId}/admins/${userId}`, {});
  }

  demote(roomId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`/api/rooms/${roomId}/admins/${userId}`);
  }
}
