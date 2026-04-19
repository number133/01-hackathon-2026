import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { NavigationEnd, Event as RouterEvent } from '@angular/router';
import { Subscription } from 'rxjs';

import { FriendService } from '../contacts/friend.service';
import { DialogService, DialogView } from '../dialog/dialog.service';
import { PresenceDotComponent } from '../presence/presence-dot.component';
import { PresenceService } from '../presence/presence.service';
import { RoomService, RoomView } from '../rooms/room.service';
import { UnreadBadgeComponent } from '../unread/unread-badge.component';
import { UnreadService } from '../unread/unread.service';
import { AvatarComponent } from './avatar.component';

@Component({
  selector: 'app-chat-sidebar',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    RouterLinkActive,
    PresenceDotComponent,
    UnreadBadgeComponent,
    AvatarComponent,
  ],
  templateUrl: './chat-sidebar.component.html',
})
export class ChatSidebarComponent implements OnInit, OnDestroy {
  private readonly roomService = inject(RoomService);
  private readonly dialogService = inject(DialogService);
  private readonly presence = inject(PresenceService);
  private readonly router = inject(Router);
  readonly friends = inject(FriendService);
  readonly unread = inject(UnreadService);

  readonly search = new FormControl<string>('', { nonNullable: true });
  readonly searchTerm = signal('');
  readonly myRooms = signal<RoomView[]>([]);
  readonly dialogs = signal<DialogView[]>([]);

  readonly publicRooms = computed(() =>
    this.filtered(this.myRooms().filter((r) => r.visibility === 'public')),
  );
  readonly privateRooms = computed(() =>
    this.filtered(this.myRooms().filter((r) => r.visibility === 'private')),
  );
  readonly contacts = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const rows = this.friends.friends();
    if (!term) return rows;
    return rows.filter((f) => f.username.toLowerCase().includes(term));
  });

  private watchedIds: string[] = [];
  private sub: Subscription | null = null;

  ngOnInit(): void {
    this.refresh();
    this.sub = this.router.events.subscribe((ev: RouterEvent) => {
      if (ev instanceof NavigationEnd) {
        this.refresh();
      }
    });
    this.search.valueChanges.subscribe((value) => {
      this.searchTerm.set(value);
      this.rewatchContacts();
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    if (this.watchedIds.length > 0) {
      this.presence.unwatch(this.watchedIds);
      this.watchedIds = [];
    }
  }

  refresh(): void {
    this.roomService.listMine().subscribe((rows) => this.myRooms.set(rows));
    this.dialogService.list().subscribe((rows) => this.dialogs.set(rows));
    this.rewatchContacts();
  }

  dialogIdFor(userId: string): string | null {
    const match = this.dialogs().find((d) => d.counterpartId === userId);
    return match ? match.id : null;
  }

  openContact(userId: string): void {
    const existing = this.dialogIdFor(userId);
    if (existing) {
      this.router.navigate(['/dialogs', existing]);
      return;
    }
    this.dialogService.open(userId).subscribe((d) => {
      this.dialogs.update((list) => (list.some((x) => x.id === d.id) ? list : [...list, d]));
      this.router.navigate(['/dialogs', d.id]);
    });
  }

  private filtered(rows: RoomView[]): RoomView[] {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) return rows;
    return rows.filter(
      (r) =>
        r.name.toLowerCase().includes(term) ||
        (r.description ?? '').toLowerCase().includes(term),
    );
  }

  private rewatchContacts(): void {
    const ids = this.friends.friends().map((f) => f.userId);
    if (this.watchedIds.length > 0) this.presence.unwatch(this.watchedIds);
    this.presence.watch(ids);
    this.watchedIds = ids;
  }
}
