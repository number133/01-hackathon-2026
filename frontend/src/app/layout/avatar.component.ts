import { CommonModule } from '@angular/common';
import { Component, Input, computed, signal } from '@angular/core';

/**
 * Circular avatar that shows the first one or two letters of a name over a
 * deterministic background hue. Purely decorative; no image fetch, no
 * branded asset.
 */
@Component({
  selector: 'app-avatar',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="avatar" [class.small]="small" [style.background]="color()">
      <span class="initials">{{ initials() }}</span>
    </span>
  `,
  styles: [
    `
      :host { display: inline-flex; }
      .avatar {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 40px;
        height: 40px;
        border-radius: 12px;
        color: #fff;
        font-family: 'Plus Jakarta Sans', sans-serif;
        font-weight: 800;
        flex-shrink: 0;
        font-size: 0.95rem;
        letter-spacing: -0.02em;
        user-select: none;
        border: 1px solid rgba(255, 255, 255, 0.45);
        box-shadow:
          0 4px 12px rgba(10, 14, 40, 0.18),
          inset 0 1px 0 rgba(255, 255, 255, 0.4);
      }
      .avatar.small {
        width: 28px; height: 28px;
        font-size: 0.72rem;
        border-radius: 9px;
      }
      .initials { line-height: 1; }
    `,
  ],
})
export class AvatarComponent {
  private readonly nameSig = signal('');

  @Input() small = false;
  @Input() set name(v: string | null | undefined) {
    this.nameSig.set((v ?? '').trim());
  }

  readonly initials = computed(() => {
    const raw = this.nameSig();
    if (!raw) return '?';
    const parts = raw.split(/\s+/).filter(Boolean);
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  });

  readonly color = computed(() => {
    const raw = this.nameSig();
    const palette = [
      'linear-gradient(135deg, #1856FF 0%, #6B8CFF 100%)',
      'linear-gradient(135deg, #0E41D6 0%, #4E7BFF 100%)',
      'linear-gradient(135deg, #3A344E 0%, #6B6288 100%)',
      'linear-gradient(135deg, #07CA6B 0%, #5FE6A4 100%)',
      'linear-gradient(135deg, #E89558 0%, #FFC59A 100%)',
      'linear-gradient(135deg, #EA2143 0%, #FF6A85 100%)',
      'linear-gradient(135deg, #8B5CF6 0%, #C4A7FB 100%)',
      'linear-gradient(135deg, #0EA5E9 0%, #7DD3FC 100%)',
      'linear-gradient(135deg, #14B8A6 0%, #5EE7D6 100%)',
      'linear-gradient(135deg, #F43F5E 0%, #FFA0B3 100%)',
      'linear-gradient(135deg, #6366F1 0%, #A5B4FC 100%)',
      'linear-gradient(135deg, #DB2777 0%, #F9A8D4 100%)',
    ];
    let hash = 0;
    for (let i = 0; i < raw.length; i++) hash = (hash * 31 + raw.charCodeAt(i)) >>> 0;
    return palette[hash % palette.length];
  });
}
