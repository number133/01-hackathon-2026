import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, computed, inject, signal } from '@angular/core';

import { AuthService } from '../auth/auth.service';
import { FriendService } from '../contacts/friend.service';
import { NotificationService } from '../core/notification/notification.service';
import { PresenceDotComponent } from '../presence/presence-dot.component';
import { RoomMemberView, RoomView } from './room.service';

@Component({
  selector: 'app-room-context-pane',
  standalone: true,
  imports: [CommonModule, PresenceDotComponent],
  templateUrl: './room-context-pane.component.html',
})
export class RoomContextPaneComponent {
  @Input({ required: true }) room!: RoomView;
  @Input() members: RoomMemberView[] = [];
  @Output() readonly manageRequested = new EventEmitter<void>();

  private readonly friends = inject(FriendService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  readonly sending = signal<string | null>(null);
  readonly myId = computed(() => this.auth.user()?.id ?? null);

  get visibilityLabel(): string {
    return this.room.visibility === 'public' ? 'Public room' : 'Private room';
  }

  admins(): RoomMemberView[] {
    return this.members.filter((m) => m.role === 'admin' || m.role === 'owner');
  }

  nonAdminMembers(): RoomMemberView[] {
    return this.members.filter((m) => m.role === 'member');
  }

  canManage(): boolean {
    return this.room.myRole === 'owner' || this.room.myRole === 'admin';
  }

  canAddFriend(m: RoomMemberView): boolean {
    const uid = this.myId();
    if (!uid || uid === m.userId) return false;
    if (this.friends.friends().some((f) => f.userId === m.userId)) return false;
    if (this.friends.outgoing().some((r) => r.recipient?.id === m.userId && r.status === 'pending')) return false;
    if (this.friends.incoming().some((r) => r.requester?.id === m.userId && r.status === 'pending')) return false;
    return true;
  }

  addFriend(m: RoomMemberView): void {
    if (!this.canAddFriend(m)) return;
    this.sending.set(m.userId);
    this.friends.send(m.username, '').subscribe({
      next: () => {
        this.sending.set(null);
        this.notifications.success(`Friend request sent to ${m.username}`);
      },
      error: (err: unknown) => {
        this.sending.set(null);
        this.notifications.error(this.auth.errorText(err));
      },
    });
  }
}
