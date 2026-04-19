import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { ComposerComponent } from '../chat/composer.component';
import { MessageListComponent } from '../chat/message-list.component';
import { NotificationService } from '../core/notification/notification.service';
import { ChatSidebarComponent } from '../layout/chat-sidebar.component';
import { ManageRoomComponent } from './manage-room.component';
import { RoomContextPaneComponent } from './room-context-pane.component';
import { RoomMemberView, RoomService, RoomView } from './room.service';

@Component({
  selector: 'app-room-view',
  standalone: true,
  imports: [
    CommonModule,
    MessageListComponent,
    ComposerComponent,
    ManageRoomComponent,
    ChatSidebarComponent,
    RoomContextPaneComponent,
  ],
  templateUrl: './room-view.component.html',
})
export class RoomViewComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly rooms = inject(RoomService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  readonly room = signal<RoomView | null>(null);
  readonly members = signal<RoomMemberView[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly myUserId = computed(() => this.auth.user()?.id ?? null);
  readonly manageOpen = signal(false);

  private paramSub?: Subscription;

  ngOnInit(): void {
    this.paramSub = this.route.paramMap.subscribe((params) => {
      const id = params.get('id');
      if (!id) {
        this.router.navigate(['/rooms']);
        return;
      }
      this.room.set(null);
      this.members.set([]);
      this.manageOpen.set(false);
      this.error.set(null);
      this.loading.set(true);
      this.load(id);
    });
  }

  ngOnDestroy(): void {
    this.paramSub?.unsubscribe();
  }

  private load(id: string): void {
    this.rooms.get(id).subscribe({
      next: (r) => {
        this.room.set(r);
        if (r.myRole) {
          this.rooms.listMembers(id).subscribe({
            next: (m) => {
              this.members.set(m);
              this.loading.set(false);
            },
            error: (err: unknown) => {
              this.loading.set(false);
              this.error.set(this.auth.errorText(err));
            },
          });
        } else {
          this.loading.set(false);
        }
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(this.auth.errorText(err));
      },
    });
  }

  join(): void {
    const r = this.room();
    if (!r) return;
    this.rooms.join(r.id).subscribe({
      next: () => {
        this.notifications.success(`Joined ${r.name}`);
        this.load(r.id);
      },
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }

  leave(): void {
    const r = this.room();
    if (!r) return;
    this.rooms.leave(r.id).subscribe({
      next: () => {
        this.notifications.success(`Left ${r.name}`);
        this.router.navigate(['/rooms']);
      },
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }
}
