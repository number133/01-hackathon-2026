import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

import { HealthResponse, HealthService } from './health.service';

interface HealthState {
  loading: boolean;
  data: HealthResponse | null;
  error: string | null;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app.component.html',
})
export class AppComponent implements OnInit {
  private readonly health = inject(HealthService);

  readonly state = signal<HealthState>({
    loading: true,
    data: null,
    error: null,
  });

  ngOnInit(): void {
    this.health.fetch().subscribe({
      next: (data) => this.state.set({ loading: false, data, error: null }),
      error: (err: { message?: string }) =>
        this.state.set({
          loading: false,
          data: null,
          error: err.message ?? 'Request failed',
        }),
    });
  }
}
