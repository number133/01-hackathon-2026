import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';

export interface CurrentUser {
  id: string;
  username: string;
  email: string;
}

export interface RegisterPayload {
  email: string;
  username: string;
  password: string;
}

export interface LoginPayload {
  email: string;
  password: string;
  rememberMe: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly currentUser = signal<CurrentUser | null>(null);
  private readonly initialized = signal(false);

  readonly user = this.currentUser.asReadonly();
  readonly ready = this.initialized.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  initialize(): Observable<CurrentUser | null> {
    return this.http.get<CurrentUser>('/api/auth/me').pipe(
      tap((user) => this.currentUser.set(user)),
      catchError((err: HttpErrorResponse) => {
        if (err.status === 401) {
          this.currentUser.set(null);
          return of(null);
        }
        throw err;
      }),
      tap(() => this.initialized.set(true)),
    );
  }

  register(payload: RegisterPayload): Observable<CurrentUser> {
    return this.http
      .post<CurrentUser>('/api/auth/register', payload)
      .pipe(tap((user) => this.currentUser.set(user)));
  }

  login(payload: LoginPayload): Observable<CurrentUser> {
    return this.http
      .post<CurrentUser>('/api/auth/login', payload)
      .pipe(tap((user) => this.currentUser.set(user)));
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {}).pipe(
      tap(() => this.currentUser.set(null)),
    );
  }

  clear(): void {
    this.currentUser.set(null);
  }

  requestPasswordReset(email: string): Observable<void> {
    return this.http.post<void>('/api/auth/password-reset/request', { email });
  }

  confirmPasswordReset(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/auth/password-reset/confirm', { token, newPassword });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/account/password', { currentPassword, newPassword });
  }

  deleteAccount(password: string): Observable<void> {
    return this.http
      .delete<void>('/api/account', { body: { password } })
      .pipe(tap(() => this.currentUser.set(null)));
  }

  errorText(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const body = err.error as { message?: string; error?: string; fields?: Record<string, string> } | null;
      if (body?.message) {
        return body.message;
      }
      if (body?.fields) {
        const first = Object.entries(body.fields)[0];
        if (first) {
          return `${first[0]}: ${first[1]}`;
        }
      }
      if (body?.error) {
        return body.error;
      }
      return err.message;
    }
    return 'Unexpected error';
  }
}
