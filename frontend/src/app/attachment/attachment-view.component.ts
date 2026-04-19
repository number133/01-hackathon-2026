import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

import { AttachmentRef, AttachmentService } from './attachment.service';

@Component({
  selector: 'app-attachment-view',
  standalone: true,
  imports: [CommonModule],
  template: `
    <figure *ngIf="attachment.isImage" class="att-image">
      <a [href]="url" target="_blank" rel="noopener">
        <img [src]="url" [alt]="attachment.originalName" />
      </a>
      <figcaption *ngIf="attachment.comment">{{ attachment.comment }}</figcaption>
    </figure>
    <div *ngIf="!attachment.isImage" class="att-card">
      <a [href]="url" target="_blank" rel="noopener">
        <strong>{{ attachment.originalName }}</strong>
        <small>{{ readableSize() }}</small>
      </a>
      <p *ngIf="attachment.comment" class="muted">{{ attachment.comment }}</p>
    </div>
  `,
  styles: [
    `
      .att-image img {
        max-width: 320px;
        max-height: 240px;
        display: block;
        border-radius: 12px;
        border: 1px solid rgba(255, 255, 255, 0.45);
        box-shadow:
          0 8px 22px rgba(10, 14, 40, 0.16),
          inset 0 1px 0 rgba(255, 255, 255, 0.4);
      }
      .att-image figcaption {
        font-size: 0.85em;
        color: rgba(20, 20, 20, 0.62);
        margin-top: 0.35rem;
        font-weight: 500;
      }
      .att-card {
        display: inline-block;
        padding: 0.65rem 0.9rem;
        border: 1px solid rgba(255, 255, 255, 0.55);
        border-radius: 12px;
        background: rgba(255, 255, 255, 0.62);
        backdrop-filter: blur(10px);
        -webkit-backdrop-filter: blur(10px);
        box-shadow:
          0 4px 14px rgba(10, 14, 40, 0.1),
          inset 0 1px 0 rgba(255, 255, 255, 0.5);
      }
      .att-card a {
        display: flex;
        flex-direction: column;
        text-decoration: none;
        color: #141414;
        font-weight: 600;
      }
      .att-card small { color: rgba(20, 20, 20, 0.62); font-size: 0.8em; font-weight: 500; }
    `,
  ],
})
export class AttachmentViewComponent {
  @Input({ required: true }) attachment!: AttachmentRef;

  private readonly service = new AttachmentService() as unknown as { downloadUrl(id: string): string };

  get url(): string {
    return `/api/attachments/${this.attachment.id}`;
  }

  readableSize(): string {
    const b = this.attachment.sizeBytes;
    if (b < 1024) return `${b} B`;
    if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} kB`;
    return `${(b / (1024 * 1024)).toFixed(1)} MB`;
  }
}
