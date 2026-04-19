import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, ViewChild, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';

import { AttachmentPickerComponent } from '../attachment/attachment-picker.component';
import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../core/notification/notification.service';
import { ChatService, MessageView } from './chat.service';
import { EmojiPickerButtonComponent } from './emoji-picker-button.component';

@Component({
  selector: 'app-composer',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    AttachmentPickerComponent,
    EmojiPickerButtonComponent,
  ],
  templateUrl: './composer.component.html',
})
export class ComposerComponent {
  @Input({ required: true }) roomId!: string;
  @Input({ required: true }) conversationId!: string;
  @Input() replyingTo: MessageView | null = null;
  @Output() clearReply = new EventEmitter<void>();

  @ViewChild(AttachmentPickerComponent) picker?: AttachmentPickerComponent;

  private readonly chat = inject(ChatService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  readonly text = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.maxLength(3072)],
  });
  readonly attachmentIds = signal<string[]>([]);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  onAttachmentsChange(ids: string[]): void {
    this.attachmentIds.set(ids);
  }

  canSend(): boolean {
    const hasText = this.text.value.trim().length > 0;
    return !this.submitting() && (hasText || this.attachmentIds().length > 0);
  }

  submit(): void {
    if (!this.canSend()) return;
    const content = this.text.value.trim();
    const ids = this.attachmentIds();
    this.submitting.set(true);
    this.error.set(null);
    const replyId = this.replyingTo ? this.replyingTo.id : null;
    this.chat.post(this.roomId, content, replyId, ids).subscribe({
      next: () => {
        this.text.reset('');
        this.picker?.clear();
        this.attachmentIds.set([]);
        this.submitting.set(false);
        if (this.replyingTo) {
          this.clearReply.emit();
        }
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const text = this.auth.errorText(err);
        this.error.set(text);
        this.notifications.error(text);
      },
    });
  }

  handleKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.submit();
    }
  }

  insertEmoji(emoji: string): void {
    const next = (this.text.value ?? '') + emoji;
    if (next.length > 3072) return;
    this.text.setValue(next);
  }
}
