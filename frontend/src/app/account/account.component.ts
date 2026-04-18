import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './account.component.html',
})
export class AccountComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly confirming = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    password: ['', [Validators.required]],
  });

  readonly user = this.auth.user;

  beginDelete(): void {
    this.confirming.set(true);
    this.error.set(null);
  }

  cancelDelete(): void {
    this.confirming.set(false);
    this.form.reset();
  }

  confirmDelete(): void {
    if (this.form.invalid) {
      return;
    }
    this.submitting.set(true);
    this.auth.deleteAccount(this.form.controls.password.value).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigateByUrl('/');
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.error.set(this.auth.errorText(err));
      },
    });
  }
}
