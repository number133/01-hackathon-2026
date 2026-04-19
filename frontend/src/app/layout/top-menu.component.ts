import { CommonModule } from '@angular/common';
import { Component, HostListener, OnInit, computed, effect, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { FriendService } from '../contacts/friend.service';
import { InvitationService } from '../rooms/invitation.service';
import { UnreadService } from '../unread/unread.service';

@Component({
  selector: 'app-top-menu',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './top-menu.component.html',
})
export class TopMenuComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly invitations = inject(InvitationService);
  private readonly friends = inject(FriendService);
  private readonly unread = inject(UnreadService);

  readonly user = this.auth.user;
  readonly pendingInvites = this.invitations.pendingCount;
  readonly pendingFriendRequests = this.friends.incomingPendingCount;
  readonly totalUnread = this.unread.total;
  readonly profileNotifications = computed(
    () => this.pendingInvites() + this.pendingFriendRequests(),
  );
  readonly profileOpen = signal(false);

  constructor() {
    effect(() => {
      if (this.auth.user()) {
        this.invitations.refreshCount();
      }
    });
  }

  ngOnInit(): void {
    if (this.auth.user()) {
      this.invitations.refreshCount();
    }
  }

  toggleProfile(): void {
    this.profileOpen.update((v) => !v);
  }

  closeProfile(): void {
    this.profileOpen.set(false);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(ev: MouseEvent): void {
    const target = ev.target as HTMLElement;
    if (!target.closest('.profile-menu')) {
      this.profileOpen.set(false);
    }
  }

  logout(): void {
    this.profileOpen.set(false);
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: () => this.router.navigateByUrl('/'),
    });
  }
}
