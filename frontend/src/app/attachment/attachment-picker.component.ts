import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, HostListener, Input, Output, inject, signal } from '@angular/core';

import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../core/notification/notification.service';
import { AttachmentService, AttachmentView } from './attachment.service';

interface PendingAttachment {
  key: string;
  file: File;
  state: 'uploading' | 'ready' | 'error';
  view?: AttachmentView;
  comment: string;
  error?: string;
}

@Component({
  selector: 'app-attachment-picker',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="picker">
      <label class="attach-btn">
        📎
        <input type="file" multiple (change)="onFilesPicked($event)" />
      </label>
      <div class="chips">
        <div *ngFor="let p of pending()" class="chip" [class.error]="p.state === 'error'">
          <span class="name">{{ p.file.name }}</span>
          <span class="muted"> — {{ readable(p.file.size) }}</span>
          <input
            type="text"
            placeholder="optional comment"
            [value]="p.comment"
            (input)="updateComment(p, $any($event.target).value)"
            [disabled]="p.state !== 'ready'"
          />
          <button type="button" class="small" (click)="remove(p)">×</button>
          <span *ngIf="p.state === 'uploading'" class="muted">uploading…</span>
          <span *ngIf="p.state === 'error'" class="error-text">{{ p.error }}</span>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .picker { display: flex; flex-direction: column; gap: 0.35rem; }
      .attach-btn {
        cursor: pointer;
        font-size: 1.2rem;
        padding: 0.35rem 0.55rem;
        border-radius: 999px;
        color: rgba(20, 20, 20, 0.62);
        transition: background 140ms ease, color 140ms ease;
      }
      .attach-btn:hover {
        background: rgba(255, 255, 255, 0.45);
        color: #141414;
      }
      .attach-btn input { display: none; }
      .chips { display: flex; flex-direction: column; gap: 0.35rem; }
      .chip {
        display: flex;
        gap: 0.5rem;
        align-items: center;
        padding: 0.4rem 0.65rem;
        background: rgba(255, 255, 255, 0.62);
        border: 1px solid rgba(255, 255, 255, 0.55);
        border-radius: 12px;
        backdrop-filter: blur(10px);
        -webkit-backdrop-filter: blur(10px);
        box-shadow: 0 2px 8px rgba(10, 14, 40, 0.06);
      }
      .chip.error {
        background: rgba(234, 33, 67, 0.1);
        border-color: rgba(234, 33, 67, 0.35);
      }
      .chip .name { font-weight: 600; }
      .chip input[type='text'] {
        background: rgba(255, 255, 255, 0.85);
        border: 1px solid rgba(20, 20, 20, 0.08);
        border-radius: 8px;
        padding: 4px 8px;
        font-size: 0.85rem;
        outline: none;
        font-family: inherit;
      }
      .chip input[type='text']:focus {
        border-color: #1856FF;
        box-shadow: 0 0 0 3px rgba(24, 86, 255, 0.15);
      }
      .error-text { color: #EA2143; font-size: 0.85em; font-weight: 600; }
    `,
  ],
})
export class AttachmentPickerComponent {
  @Input({ required: true }) conversationId!: string;
  @Output() idsChange = new EventEmitter<string[]>();

  private readonly service = inject(AttachmentService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  readonly pending = signal<PendingAttachment[]>([]);

  onFilesPicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    Array.from(input.files).forEach((f) => this.enqueue(f));
    input.value = '';
  }

  @HostListener('paste', ['$event'])
  onPaste(event: ClipboardEvent): void {
    const files = event.clipboardData?.files;
    if (!files || files.length === 0) return;
    Array.from(files).forEach((f) => this.enqueue(f));
  }

  updateComment(p: PendingAttachment, value: string): void {
    p.comment = value;
  }

  remove(p: PendingAttachment): void {
    if (p.state === 'ready' && p.view) {
      this.service.cancel(p.view.id).subscribe({ error: () => undefined });
    }
    this.pending.update((list) => list.filter((x) => x.key !== p.key));
    this.emit();
  }

  clear(): void {
    // Let the composer clear chips after a successful send.
    this.pending.set([]);
    this.emit();
  }

  enqueueFiles(files: FileList | File[] | null): number {
    if (!files) return 0;
    const arr = Array.from(files as ArrayLike<File>);
    arr.forEach((f) => this.enqueue(f));
    return arr.length;
  }

  private enqueue(file: File): void {
    const key = crypto.randomUUID();
    const entry: PendingAttachment = { key, file, state: 'uploading', comment: '' };
    this.pending.update((list) => [...list, entry]);
    this.service.upload(file, this.conversationId, null).subscribe({
      next: (view) => {
        this.pending.update((list) =>
          list.map((p) => (p.key === key ? { ...p, state: 'ready', view } : p)),
        );
        this.emit();
      },
      error: (err: unknown) => {
        const msg = err instanceof HttpErrorResponse ? this.auth.errorText(err) : 'Upload failed';
        this.pending.update((list) =>
          list.map((p) => (p.key === key ? { ...p, state: 'error', error: msg } : p)),
        );
        this.notifications.error(`${file.name}: ${msg}`);
      },
    });
  }

  private emit(): void {
    const ids = this.pending()
      .filter((p) => p.state === 'ready' && p.view)
      .map((p) => p.view!.id);
    this.idsChange.emit(ids);
  }

  readable(b: number): string {
    if (b < 1024) return `${b} B`;
    if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} kB`;
    return `${(b / (1024 * 1024)).toFixed(1)} MB`;
  }
}
