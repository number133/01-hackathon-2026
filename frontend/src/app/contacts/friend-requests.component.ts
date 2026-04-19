import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../core/notification/notification.service';
import { FriendService } from './friend.service';

@Component({
  selector: 'app-friend-requests',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './friend-requests.component.html',
})
export class FriendRequestsComponent {
  private readonly friends = inject(FriendService);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(40)]],
    message: ['', [Validators.maxLength(500)]],
  });

  readonly incoming = this.friends.incoming;
  readonly outgoing = this.friends.outgoing;
  readonly error = signal<string | null>(null);

  send(): void {
    if (this.form.invalid) return;
    const { username, message } = this.form.getRawValue();
    this.friends.send(username, message).subscribe({
      next: () => {
        this.form.reset({ username: '', message: '' });
        this.error.set(null);
        this.notifications.success(`Friend request sent to ${username}`);
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  accept(id: string): void {
    this.friends.accept(id).subscribe({
      next: () => this.notifications.success('Friend request accepted'),
      error: (err: unknown) => this.handleError(err),
    });
  }
  decline(id: string): void {
    this.friends.decline(id).subscribe({
      next: () => this.notifications.info('Friend request declined'),
      error: (err: unknown) => this.handleError(err),
    });
  }
  revoke(id: string): void {
    this.friends.revoke(id).subscribe({
      next: () => this.notifications.info('Friend request revoked'),
      error: (err: unknown) => this.handleError(err),
    });
  }

  private handleError(err: unknown): void {
    const text = this.auth.errorText(err);
    this.error.set(text);
    this.notifications.error(text);
  }
}
