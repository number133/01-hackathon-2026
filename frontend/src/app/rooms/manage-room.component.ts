import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';
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
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './manage-room.component.html',
})
export class ManageRoomComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly rooms = inject(RoomService);
  private readonly invitations = inject(InvitationService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

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
      next: (m) => this.members.set(m),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
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

  kick(userId: string): void {
    const id = this.roomId();
    if (!id) return;
    this.rooms.kick(id, userId).subscribe({
      next: () => this.loadMembers(id),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  ban(userId: string): void {
    const id = this.roomId();
    if (!id) return;
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
