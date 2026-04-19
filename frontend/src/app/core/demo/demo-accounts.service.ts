import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of } from 'rxjs';

export interface DemoAccount {
  email: string;
  username: string;
  password: string;
}

@Injectable({ providedIn: 'root' })
export class DemoAccountsService {
  private readonly http = inject(HttpClient);

  list(): Observable<DemoAccount[]> {
    return this.http
      .get<DemoAccount[]>('/api/dev/demo-accounts')
      .pipe(catchError(() => of<DemoAccount[]>([])));
  }
}
