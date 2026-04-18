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
        border-radius: 4px;
      }
      .att-image figcaption { font-size: 0.85em; color: #666; margin-top: 0.25rem; }
      .att-card {
        display: inline-block;
        padding: 0.5rem 0.75rem;
        border: 1px solid #ccc;
        border-radius: 4px;
        background: #f6f6f6;
      }
      .att-card a { display: flex; flex-direction: column; text-decoration: none; color: #222; }
      .att-card small { color: #666; font-size: 0.8em; }
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
