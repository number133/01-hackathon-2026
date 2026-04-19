import { CommonModule } from '@angular/common';
import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../core/notification/notification.service';
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
  imports: [CommonModule, ReactiveFormsModule, PresenceDotComponent],
  templateUrl: './manage-room.component.html',
})
export class ManageRoomComponent implements OnInit, OnChanges, OnDestroy {
  private readonly rooms = inject(RoomService);
  private readonly invitations = inject(InvitationService);
  private readonly auth = inject(AuthService);
  private readonly presence = inject(PresenceService);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  @Input({ required: true }) roomId!: string;
  @Input() myRole: 'owner' | 'admin' | 'member' | null = null;
  @Output() readonly closed = new EventEmitter<void>();

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

  readonly pendingRemove = signal<{ userId: string; username: string } | null>(null);
  readonly pendingBan = signal<{ userId: string; username: string } | null>(null);
  readonly banReasonCtrl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(200)],
  });
  readonly confirmingDelete = signal(false);

  readonly isAdminOrOwner = computed(
    () => this.myRole === 'admin' || this.myRole === 'owner',
  );

  ngOnInit(): void {
    this.loadRoom(this.roomId);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['roomId'] && !changes['roomId'].firstChange) {
      this.tab.set('members');
      this.pendingRemove.set(null);
      this.pendingBan.set(null);
      this.confirmingDelete.set(false);
      this.loadRoom(this.roomId);
    }
  }

  ngOnDestroy(): void {
    if (this.watchedIds.length > 0) {
      this.presence.unwatch(this.watchedIds);
      this.watchedIds = [];
    }
  }

  close(): void {
    this.closed.emit();
  }

  setTab(t: Tab): void {
    if ((t === 'banned' || t === 'invitations') && !this.isAdminOrOwner()) return;
    this.tab.set(t);
    if (t === 'members' || t === 'admins') this.loadMembers(this.roomId);
    if (t === 'banned') this.loadBans(this.roomId);
    if (t === 'invitations') this.loadInvites(this.roomId);
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

  private loadBans(id: string): void {
    this.rooms.listBans(id).subscribe({
      next: (b) => this.bans.set(b),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  private loadInvites(id: string): void {
    this.invitations.listForRoom(id).subscribe({
      next: (list) => this.roomInvites.set(list),
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

  presenceStatus(userId: string): string {
    return this.presence.status(userId);
  }

  promote(userId: string): void {
    this.rooms.promote(this.roomId, userId).subscribe({
      next: () => this.loadMembers(this.roomId),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  demote(userId: string): void {
    this.rooms.demote(this.roomId, userId).subscribe({
      next: () => this.loadMembers(this.roomId),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  requestRemove(userId: string, username: string): void {
    this.pendingBan.set(null);
    this.pendingRemove.set({ userId, username });
  }

  cancelRemove(): void {
    this.pendingRemove.set(null);
  }

  confirmRemove(): void {
    const pending = this.pendingRemove();
    if (!pending) return;
    this.pendingRemove.set(null);
    this.rooms.remove(this.roomId, pending.userId).subscribe({
      next: () => {
        this.notifications.success(`Removed ${pending.username}`);
        this.loadMembers(this.roomId);
      },
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }

  requestBan(userId: string, username: string): void {
    this.pendingRemove.set(null);
    this.banReasonCtrl.reset('');
    this.pendingBan.set({ userId, username });
  }

  cancelBan(): void {
    this.pendingBan.set(null);
  }

  confirmBan(): void {
    const pending = this.pendingBan();
    if (!pending || this.banReasonCtrl.invalid) return;
    const reason = this.banReasonCtrl.value.trim();
    this.pendingBan.set(null);
    this.rooms.ban(this.roomId, pending.userId, reason.length > 0 ? reason : null).subscribe({
      next: () => {
        this.notifications.success(`Banned ${pending.username}`);
        this.loadMembers(this.roomId);
        this.loadBans(this.roomId);
      },
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }

  unban(userId: string): void {
    this.rooms.unban(this.roomId, userId).subscribe({
      next: () => {
        this.notifications.success('User unbanned');
        this.loadBans(this.roomId);
      },
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }

  sendInvite(): void {
    if (this.inviteUsername.invalid) return;
    const { username, message } = this.inviteUsername.getRawValue();
    this.invitations.invite(this.roomId, username, message).subscribe({
      next: () => {
        this.inviteUsername.reset({ username: '', message: '' });
        this.error.set(null);
        this.notifications.success(`Invite sent to ${username}`);
        this.loadInvites(this.roomId);
      },
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }

  revokeInvite(invitationId: string): void {
    this.invitations.revoke(invitationId).subscribe({
      next: () => {
        this.notifications.success('Invitation revoked');
        this.loadInvites(this.roomId);
      },
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }

  saveSettings(): void {
    if (this.settingsForm.invalid) return;
    const patch: UpdateRoomPayload = this.settingsForm.getRawValue();
    this.rooms.update(this.roomId, patch).subscribe({
      next: (r) => {
        this.room.set(r);
        this.notifications.success('Room settings saved');
      },
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }

  requestDeleteRoom(): void {
    this.confirmingDelete.set(true);
  }

  cancelDeleteRoom(): void {
    this.confirmingDelete.set(false);
  }

  confirmDeleteRoom(): void {
    const roomName = this.room()?.name ?? 'Room';
    this.rooms.delete(this.roomId).subscribe({
      next: () => {
        this.confirmingDelete.set(false);
        this.notifications.success(`${roomName} deleted`);
        this.closed.emit();
      },
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }
}
