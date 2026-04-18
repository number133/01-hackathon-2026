import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { RoomMemberView, RoomService, RoomView } from './room.service';

@Component({
  selector: 'app-room-view',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './room-view.component.html',
})
export class RoomViewComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly rooms = inject(RoomService);
  private readonly auth = inject(AuthService);

  readonly room = signal<RoomView | null>(null);
  readonly members = signal<RoomMemberView[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.router.navigate(['/rooms']);
      return;
    }
    this.load(id);
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
      next: () => this.load(r.id),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  leave(): void {
    const r = this.room();
    if (!r) return;
    this.rooms.leave(r.id).subscribe({
      next: () => this.router.navigate(['/rooms']),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }
}
