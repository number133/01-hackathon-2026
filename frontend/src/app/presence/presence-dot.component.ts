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
        background: #9AA0B4;
        margin-right: 0.35rem;
        vertical-align: middle;
        box-shadow:
          0 0 0 2px rgba(255, 255, 255, 0.9),
          0 2px 6px rgba(10, 14, 40, 0.25);
      }
      .presence-dot.online {
        background: radial-gradient(circle at 35% 30%, #5FE6A4, #07CA6B 80%);
        box-shadow:
          0 0 0 2px rgba(255, 255, 255, 0.9),
          0 0 10px rgba(7, 202, 107, 0.55);
      }
      .presence-dot.afk {
        background: radial-gradient(circle at 35% 30%, #FFC59A, #E89558 80%);
      }
      .presence-dot.offline {
        background: radial-gradient(circle at 35% 30%, #C8CCDC, #9AA0B4 80%);
      }
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
