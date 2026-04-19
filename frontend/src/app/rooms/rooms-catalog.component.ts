import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../core/notification/notification.service';
import { UnreadBadgeComponent } from '../unread/unread-badge.component';
import { UnreadService } from '../unread/unread.service';
import { RoomService, RoomView } from './room.service';

@Component({
  selector: 'app-rooms-catalog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, UnreadBadgeComponent],
  templateUrl: './rooms-catalog.component.html',
})
export class RoomsCatalogComponent implements OnInit {
  private readonly roomService = inject(RoomService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  readonly unread = inject(UnreadService);

  readonly search = new FormControl<string>('', { nonNullable: true });
  readonly rooms = signal<RoomView[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.refresh('');
    this.search.valueChanges
      .pipe(debounceTime(250), distinctUntilChanged(), switchMap((q) => {
        this.loading.set(true);
        return this.roomService.listCatalog(q);
      }))
      .subscribe({
        next: (list) => {
          this.rooms.set(list);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          this.error.set(this.auth.errorText(err));
        },
      });
  }

  refresh(q: string): void {
    this.loading.set(true);
    this.roomService.listCatalog(q).subscribe({
      next: (list) => {
        this.rooms.set(list);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(this.auth.errorText(err));
      },
    });
  }

  join(room: RoomView): void {
    this.roomService.join(room.id).subscribe({
      next: () => {
        this.notifications.success(`Joined ${room.name}`);
        this.router.navigate(['/rooms', room.id]);
      },
      error: (err: unknown) => {
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }
}
