import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { DemoAccount, DemoAccountsService } from '../core/demo/demo-accounts.service';
import { NotificationService } from '../core/notification/notification.service';
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
  private readonly demoAccounts = inject(DemoAccountsService);
  private readonly notifications = inject(NotificationService);

  readonly user = this.auth.user;
  readonly state = signal<HealthState>({ loading: true, data: null, error: null });
  readonly demo = signal<DemoAccount[]>([]);

  ngOnInit(): void {
    this.health.fetch().subscribe({
      next: (data) => this.state.set({ loading: false, data, error: null }),
      error: (err: { message?: string }) =>
        this.state.set({ loading: false, data: null, error: err.message ?? 'Request failed' }),
    });
    this.demoAccounts.list().subscribe((list) => this.demo.set(list));
  }

  copy(text: string): void {
    const done = () => this.notifications.success(`Copied ${text}`);
    const fail = () => this.notifications.error('Clipboard not available');
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(done, fail);
    } else {
      fail();
    }
  }
}
