import { Routes } from '@angular/router';

import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'login',
    loadComponent: () => import('./auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () => import('./auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./auth/forgot-password.component').then((m) => m.ForgotPasswordComponent),
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./auth/reset-password.component').then((m) => m.ResetPasswordComponent),
  },
  {
    path: 'account',
    canActivate: [authGuard],
    loadComponent: () => import('./account/account.component').then((m) => m.AccountComponent),
  },
  {
    path: 'account/password',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./auth/change-password.component').then((m) => m.ChangePasswordComponent),
  },
  {
    path: 'sessions',
    canActivate: [authGuard],
    loadComponent: () => import('./account/sessions.component').then((m) => m.SessionsComponent),
  },
  { path: '**', redirectTo: '' },
];
