import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { DialogService } from '../dialog/dialog.service';
import { PresenceDotComponent } from '../presence/presence-dot.component';
import { PresenceService } from '../presence/presence.service';
import { FriendService } from './friend.service';

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
  private readonly router = inject(Router);

  readonly error = signal<string | null>(null);
  readonly list = this.friends.friends;
  readonly bans = this.friends.bans;

  private watched: string[] = [];

  ngOnInit(): void {
    this.friends.refreshAll();
    // Watch presence for every current friend; refresh watch set when list changes.
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
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  unfriend(userId: string, username: string): void {
    if (!confirm(`Unfriend ${username}? They stay reachable by a fresh request.`)) return;
    this.friends.unfriend(userId).subscribe({
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  ban(userId: string, username: string): void {
    if (!confirm(`Block ${username}? Ends the friendship, freezes the dialog, blocks new DMs.`)) return;
    this.friends.ban(userId).subscribe({
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  unban(userId: string): void {
    this.friends.unban(userId).subscribe({
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }
}
