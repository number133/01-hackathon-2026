import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { RoomService } from './room.service';

@Component({
  selector: 'app-create-room',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './create-room.component.html',
})
export class CreateRoomComponent {
  private readonly fb = inject(FormBuilder);
  private readonly rooms = inject(RoomService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(80)]],
    description: ['', [Validators.maxLength(2000)]],
    visibility: ['public' as 'public' | 'private', [Validators.required]],
  });

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.rooms.create(this.form.getRawValue()).subscribe({
      next: (room) => {
        this.submitting.set(false);
        this.router.navigate(['/rooms', room.id]);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.error.set(this.auth.errorText(err));
      },
    });
  }
}
