import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../auth/auth.service';
import { ChatService, MessageView } from './chat.service';

@Component({
  selector: 'app-message-item',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './message-item.component.html',
})
export class MessageItemComponent {
  @Input({ required: true }) message!: MessageView;
  @Input() canEdit = false;
  @Input() canDelete = false;
  @Output() reply = new EventEmitter<MessageView>();

  private readonly chat = inject(ChatService);
  private readonly auth = inject(AuthService);

  readonly editing = signal(false);
  readonly error = signal<string | null>(null);
  readonly draft = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(3072)],
  });

  startEdit(): void {
    this.draft.setValue(this.message.body ?? '');
    this.editing.set(true);
    this.error.set(null);
  }

  cancelEdit(): void {
    this.editing.set(false);
  }

  saveEdit(): void {
    if (this.draft.invalid) return;
    this.chat.edit(this.message.id, this.draft.value).subscribe({
      next: () => this.editing.set(false),
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  deleteMessage(): void {
    this.chat.delete(this.message.id).subscribe({
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  emitReply(): void {
    this.reply.emit(this.message);
  }
}
