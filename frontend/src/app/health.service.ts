import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface HealthResponse {
  status: string;
  db: string;
  flywayVersion: string | null;
  error?: string;
}

@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly http = inject(HttpClient);

  fetch(): Observable<HealthResponse> {
    return this.http.get<HealthResponse>('/api/health');
  }
}
