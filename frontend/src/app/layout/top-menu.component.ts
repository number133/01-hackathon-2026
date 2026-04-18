import { CommonModule } from '@angular/common';
import { Component, OnInit, effect, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { InvitationService } from '../rooms/invitation.service';

@Component({
  selector: 'app-top-menu',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './top-menu.component.html',
})
export class TopMenuComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly invitations = inject(InvitationService);

  readonly user = this.auth.user;
  readonly pendingInvites = this.invitations.pendingCount;

  constructor() {
    effect(() => {
      if (this.auth.user()) {
        this.invitations.refreshCount();
      }
    });
  }

  ngOnInit(): void {
    if (this.auth.user()) {
      this.invitations.refreshCount();
    }
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: () => this.router.navigateByUrl('/'),
    });
  }
}
