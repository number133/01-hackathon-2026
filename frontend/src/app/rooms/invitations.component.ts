import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../core/notification/notification.service';
import { InvitationService, InvitationView } from './invitation.service';

@Component({
  selector: 'app-invitations',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './invitations.component.html',
})
export class InvitationsComponent implements OnInit {
  private readonly invitations = inject(InvitationService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);

  readonly items = signal<InvitationView[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.refresh();
  }

  private refresh(): void {
    this.loading.set(true);
    this.invitations.listMine().subscribe({
      next: (list) => {
        this.items.set(list);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.handleError(err);
      },
    });
  }

  accept(inv: InvitationView): void {
    this.invitations.accept(inv.id).subscribe({
      next: () => {
        this.notifications.success(`Joined ${inv.roomName}`);
        this.router.navigate(['/rooms', inv.roomId]);
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  decline(inv: InvitationView): void {
    this.invitations.decline(inv.id).subscribe({
      next: () => {
        this.notifications.info(`Declined invitation to ${inv.roomName}`);
        this.refresh();
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  private handleError(err: unknown): void {
    const text = this.auth.errorText(err);
    this.error.set(text);
    this.notifications.error(text);
  }
}
