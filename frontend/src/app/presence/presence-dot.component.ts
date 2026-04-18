import { CommonModule } from '@angular/common';
import { Component, Input, inject } from '@angular/core';

import { PresenceService, PresenceStatus } from './presence.service';

@Component({
  selector: 'app-presence-dot',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span
      class="presence-dot"
      [class.online]="status() === 'online'"
      [class.afk]="status() === 'afk'"
      [class.offline]="status() === 'offline'"
      [attr.title]="status()"
      aria-hidden="true"
    ></span>
  `,
  styles: [
    `
      .presence-dot {
        display: inline-block;
        width: 0.6rem;
        height: 0.6rem;
        border-radius: 50%;
        background: var(--presence-offline, #9aa0a6);
        margin-right: 0.35rem;
        vertical-align: middle;
      }
      .presence-dot.online { background: var(--presence-online, #34a853); }
      .presence-dot.afk    { background: var(--presence-afk, #f9ab00); }
      .presence-dot.offline { background: var(--presence-offline, #9aa0a6); }
    `,
  ],
})
export class PresenceDotComponent {
  private readonly presence = inject(PresenceService);

  @Input({ required: true }) userId!: string;

  status(): PresenceStatus {
    return this.presence.status(this.userId);
  }
}
