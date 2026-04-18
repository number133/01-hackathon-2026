import { APP_INITIALIZER, ApplicationConfig } from '@angular/core';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
  withXsrfConfiguration,
} from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { AuthService } from './auth/auth.service';
import { authErrorInterceptor } from './auth/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([authErrorInterceptor]),
    ),
    {
      provide: APP_INITIALIZER,
      multi: true,
      deps: [AuthService],
      useFactory: (auth: AuthService) => () => firstValueFrom(auth.initialize()),
    },
  ],
};

export type Http = HttpClient;
