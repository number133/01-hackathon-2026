import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { PresenceDotComponent } from '../presence/presence-dot.component';
import { PresenceService } from '../presence/presence.service';
import { UnreadBadgeComponent } from '../unread/unread-badge.component';
import { UnreadService } from '../unread/unread.service';
import { DialogService, DialogView } from './dialog.service';

@Component({
  selector: 'app-dialogs-catalog',
  standalone: true,
  imports: [CommonModule, RouterLink, PresenceDotComponent, UnreadBadgeComponent],
  templateUrl: './dialogs-catalog.component.html',
})
export class DialogsCatalogComponent {
  private readonly dialogs = inject(DialogService);
  private readonly auth = inject(AuthService);
  private readonly presence = inject(PresenceService);
  readonly unread = inject(UnreadService);

  readonly items = signal<DialogView[]>([]);
  readonly error = signal<string | null>(null);

  private watched: string[] = [];

  ngOnInit(): void {
    this.dialogs.list().subscribe({
      next: (xs) => {
        this.items.set(xs);
        const ids = xs.map((d) => d.counterpartId);
        if (this.watched.length > 0) this.presence.unwatch(this.watched);
        this.presence.watch(ids);
        this.watched = ids;
      },
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  ngOnDestroy(): void {
    if (this.watched.length > 0) this.presence.unwatch(this.watched);
  }
}
