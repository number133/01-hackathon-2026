import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../core/notification/notification.service';
import { DialogService } from '../dialog/dialog.service';
import { PresenceDotComponent } from '../presence/presence-dot.component';
import { PresenceService } from '../presence/presence.service';
import { FriendService } from './friend.service';

interface PendingAction {
  userId: string;
  username: string;
}

@Component({
  selector: 'app-contacts',
  standalone: true,
  imports: [CommonModule, RouterLink, PresenceDotComponent],
  templateUrl: './contacts.component.html',
})
export class ContactsComponent {
  private readonly friends = inject(FriendService);
  private readonly dialogs = inject(DialogService);
  private readonly presence = inject(PresenceService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);

  readonly error = signal<string | null>(null);
  readonly list = this.friends.friends;
  readonly bans = this.friends.bans;
  readonly pendingUnfriend = signal<PendingAction | null>(null);
  readonly pendingBan = signal<PendingAction | null>(null);

  private watched: string[] = [];

  ngOnInit(): void {
    this.friends.refreshAll();
    this.rewatch();
  }

  rewatch(): void {
    const ids = this.list().map((f) => f.userId);
    if (this.watched.length > 0) this.presence.unwatch(this.watched);
    this.presence.watch(ids);
    this.watched = ids;
  }

  ngOnDestroy(): void {
    if (this.watched.length > 0) this.presence.unwatch(this.watched);
  }

  openDialog(userId: string): void {
    this.dialogs.open(userId).subscribe({
      next: (d) => this.router.navigate(['/dialogs', d.id]),
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }

  requestUnfriend(userId: string, username: string): void {
    this.pendingBan.set(null);
    this.pendingUnfriend.set({ userId, username });
  }

  confirmUnfriend(): void {
    const pending = this.pendingUnfriend();
    if (!pending) return;
    this.pendingUnfriend.set(null);
    this.friends.unfriend(pending.userId).subscribe({
      next: () => this.notifications.success(`Unfriended ${pending.username}`),
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }

  cancelUnfriend(): void {
    this.pendingUnfriend.set(null);
  }

  requestBan(userId: string, username: string): void {
    this.pendingUnfriend.set(null);
    this.pendingBan.set({ userId, username });
  }

  confirmBan(): void {
    const pending = this.pendingBan();
    if (!pending) return;
    this.pendingBan.set(null);
    this.friends.ban(pending.userId).subscribe({
      next: () => this.notifications.success(`Blocked ${pending.username}`),
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }

  cancelBan(): void {
    this.pendingBan.set(null);
  }

  unban(userId: string, username: string): void {
    this.friends.unban(userId).subscribe({
      next: () => this.notifications.success(`Unblocked ${username}`),
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }
}
