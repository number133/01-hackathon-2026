import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { HealthResponse, HealthService } from '../health.service';

interface HealthState {
  loading: boolean;
  data: HealthResponse | null;
  error: string | null;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './home.component.html',
})
export class HomeComponent implements OnInit {
  private readonly health = inject(HealthService);
  private readonly auth = inject(AuthService);

  readonly user = this.auth.user;
  readonly state = signal<HealthState>({ loading: true, data: null, error: null });

  ngOnInit(): void {
    this.health.fetch().subscribe({
      next: (data) => this.state.set({ loading: false, data, error: null }),
      error: (err: { message?: string }) =>
        this.state.set({ loading: false, data: null, error: err.message ?? 'Request failed' }),
    });
  }
}
