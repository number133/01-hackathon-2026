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
        min-width: 1.2rem;
        padding: 0.1rem 0.5rem;
        margin-left: 0.4rem;
        background: linear-gradient(135deg, #EA2143 0%, #FF6A85 100%);
        color: #fff;
        font-size: 0.72rem;
        font-weight: 800;
        border-radius: 999px;
        text-align: center;
        line-height: 1.15rem;
        letter-spacing: 0.02em;
        border: 1px solid rgba(255, 255, 255, 0.35);
        box-shadow:
          0 4px 10px rgba(234, 33, 67, 0.35),
          inset 0 1px 0 rgba(255, 255, 255, 0.3);
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
