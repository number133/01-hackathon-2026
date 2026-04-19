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
    path: 'invitations',
    canActivate: [authGuard],
    loadComponent: () => import('./rooms/invitations.component').then((m) => m.InvitationsComponent),
  },
  {
    path: 'contacts',
    canActivate: [authGuard],
    loadComponent: () => import('./contacts/contacts.component').then((m) => m.ContactsComponent),
  },
  {
    path: 'friend-requests',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./contacts/friend-requests.component').then((m) => m.FriendRequestsComponent),
  },
  {
    path: 'dialogs',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./dialog/dialogs-catalog.component').then((m) => m.DialogsCatalogComponent),
  },
  {
    path: 'dialogs/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./dialog/dialog-view.component').then((m) => m.DialogViewComponent),
  },
  { path: '**', redirectTo: '' },
];
