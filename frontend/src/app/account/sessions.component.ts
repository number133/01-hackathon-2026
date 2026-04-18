import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';

interface SessionView {
  sessionId: string;
  createdAt: string;
  lastAccessedAt: string;
  ip: string;
  userAgent: string;
  current: boolean;
}

@Component({
  selector: 'app-sessions',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sessions.component.html',
})
export class SessionsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly sessions = signal<SessionView[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.http.get<SessionView[]>('/api/sessions').subscribe({
      next: (list) => {
        this.sessions.set(list);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(this.auth.errorText(err));
      },
    });
  }

  revoke(session: SessionView): void {
    this.http.delete(`/api/sessions/${encodeURIComponent(session.sessionId)}`).subscribe({
      next: () => {
        if (session.current) {
          this.auth.clear();
          this.router.navigateByUrl('/login');
        } else {
          this.refresh();
        }
      },
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }
}
