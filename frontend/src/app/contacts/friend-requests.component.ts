import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../auth/auth.service';
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
      },
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  accept(id: string): void {
    this.friends.accept(id).subscribe({
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }
  decline(id: string): void {
    this.friends.decline(id).subscribe({
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }
  revoke(id: string): void {
    this.friends.revoke(id).subscribe({
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }
}
