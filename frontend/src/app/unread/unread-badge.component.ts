import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-unread-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span *ngIf="count > 0" class="unread-badge">{{ display() }}</span>
  `,
  styles: [
    `
      .unread-badge {
        display: inline-block;
        min-width: 1.1rem;
        padding: 0.05rem 0.4rem;
        margin-left: 0.4rem;
        background: #d93025;
        color: #fff;
        font-size: 0.75rem;
        font-weight: 600;
        border-radius: 999px;
        text-align: center;
        line-height: 1.1rem;
      }
    `,
  ],
})
export class UnreadBadgeComponent {
  @Input() count = 0;

  display(): string {
    return this.count > 99 ? '99+' : String(this.count);
  }
}
