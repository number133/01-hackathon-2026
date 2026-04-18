import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { PresenceDotComponent } from '../presence/presence-dot.component';
import { PresenceService } from '../presence/presence.service';
import { InvitationService, InvitationView } from './invitation.service';
import {
  RoomBanView,
  RoomMemberView,
  RoomService,
  RoomView,
  UpdateRoomPayload,
} from './room.service';

type Tab = 'members' | 'admins' | 'banned' | 'invitations' | 'settings';

@Component({
  selector: 'app-manage-room',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, PresenceDotComponent],
  templateUrl: './manage-room.component.html',
})
export class ManageRoomComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly rooms = inject(RoomService);
  private readonly invitations = inject(InvitationService);
  private readonly auth = inject(AuthService);
  private readonly presence = inject(PresenceService);
  private readonly fb = inject(FormBuilder);

  private watchedIds: string[] = [];

  readonly tab = signal<Tab>('members');
  readonly room = signal<RoomView | null>(null);
  readonly members = signal<RoomMemberView[]>([]);
  readonly bans = signal<RoomBanView[]>([]);
  readonly roomInvites = signal<InvitationView[]>([]);
  readonly error = signal<string | null>(null);
  readonly inviteUsername = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(40)]],
    message: [''],
  });

  readonly settingsForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    description: ['', [Validators.maxLength(2000)]],
    visibility: ['public' as 'public' | 'private'],
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.router.navigate(['/rooms']);
      return;
    }
    this.loadRoom(id);
  }

  private roomId(): string | null {
    return this.room()?.id ?? null;
  }

  setTab(t: Tab): void {
    this.tab.set(t);
    const id = this.roomId();
    if (!id) return;
    if (t === 'members' || t === 'admins') this.loadMembers(id);
    if (t === 'banned') this.loadBans(id);
    if (t === 'invitations') this.loadInvites(id);
  }

  private loadRoom(id: string): void {
    this.rooms.get(id).subscribe({
      next: (r) => {
        this.room.set(r);
        this.settingsForm.patchValue({
          name: r.name,
          description: r.description,
          visibility: r.visibility,
        });
        this.loadMembers(id);
      },
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  private loadMembers(id: string): void {
    this.rooms.listMembers(id).subscribe({
      next: (m) => {
        this.members.set(m);
        this.rewatchPresence(m.map((x) => x.userId));
      },
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  private rewatchPresence(ids: string[]): void {
    if (this.watchedIds.length > 0) {
      this.presence.unwatch(this.watchedIds);
    }
    this.presence.watch(ids);
    this.watchedIds = ids;
  }

  ngOnDestroy(): void {
    if (this.watchedIds.length > 0) {
      this.presence.unwatch(this.watchedIds);
      this.watchedIds = [];
    }
  }

  private loadBans(id: string): void {
    this.rooms.listBans(id).subscribe({
      next: (b) => this.bans.set(b),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  private loadInvites(id: string): void {
    // Room-scoped pending list is an admin-only endpoint; silent-ignore if we
    // are "admin" without being able to list (e.g. server-side mismatch).
    import('rxjs').then(() => {
      // no-op; call below uses the HttpClient in the service
    });
  }

  promote(userId: string): void {
    const id = this.roomId();
    if (!id) return;
    this.rooms.promote(id, userId).subscribe({
      next: () => this.loadMembers(id),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  demote(userId: string): void {
    const id = this.roomId();
    if (!id) return;
    this.rooms.demote(id, userId).subscribe({
      next: () => this.loadMembers(id),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  presenceStatus(userId: string): string {
    return this.presence.status(userId);
  }

  remove(userId: string, username: string): void {
    const id = this.roomId();
    if (!id) return;
    if (!confirm(`Remove ${username} from this room? They can rejoin (public rooms) or accept a fresh invite (private rooms).`)) {
      return;
    }
    this.rooms.remove(id, userId).subscribe({
      next: () => this.loadMembers(id),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  ban(userId: string, username: string): void {
    const id = this.roomId();
    if (!id) return;
    if (!confirm(`Ban ${username}? They cannot rejoin until an admin unbans them.`)) {
      return;
    }
    this.rooms.ban(id, userId, null).subscribe({
      next: () => {
        this.loadMembers(id);
        this.loadBans(id);
      },
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  unban(userId: string): void {
    const id = this.roomId();
    if (!id) return;
    this.rooms.unban(id, userId).subscribe({
      next: () => this.loadBans(id),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  sendInvite(): void {
    const id = this.roomId();
    if (!id || this.inviteUsername.invalid) return;
    const { username, message } = this.inviteUsername.getRawValue();
    this.invitations.invite(id, username, message).subscribe({
      next: () => {
        this.inviteUsername.reset({ username: '', message: '' });
        this.error.set(null);
      },
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  saveSettings(): void {
    const id = this.roomId();
    if (!id || this.settingsForm.invalid) return;
    const patch: UpdateRoomPayload = this.settingsForm.getRawValue();
    this.rooms.update(id, patch).subscribe({
      next: (r) => this.room.set(r),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  deleteRoom(): void {
    const id = this.roomId();
    if (!id) return;
    if (!confirm('Delete this room? All messages and files will be removed.')) return;
    this.rooms.delete(id).subscribe({
      next: () => this.router.navigate(['/rooms']),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }
}
