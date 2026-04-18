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
  {
    path: 'rooms',
    canActivate: [authGuard],
    loadComponent: () => import('./rooms/rooms-catalog.component').then((m) => m.RoomsCatalogComponent),
  },
  {
    path: 'rooms/new',
    canActivate: [authGuard],
    loadComponent: () => import('./rooms/create-room.component').then((m) => m.CreateRoomComponent),
  },
  {
    path: 'rooms/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./rooms/room-view.component').then((m) => m.RoomViewComponent),
  },
  {
    path: 'rooms/:id/manage',
    canActivate: [authGuard],
    loadComponent: () => import('./rooms/manage-room.component').then((m) => m.ManageRoomComponent),
  },
  {
    path: 'invitations',
    canActivate: [authGuard],
    loadComponent: () => import('./rooms/invitations.component').then((m) => m.InvitationsComponent),
  },
  { path: '**', redirectTo: '' },
];
