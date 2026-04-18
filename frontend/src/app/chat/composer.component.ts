import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../auth/auth.service';
import { ChatService, MessageView } from './chat.service';

@Component({
  selector: 'app-composer',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './composer.component.html',
})
export class ComposerComponent {
  @Input({ required: true }) roomId!: string;
  @Input() replyingTo: MessageView | null = null;
  @Output() clearReply = new EventEmitter<void>();

  private readonly chat = inject(ChatService);
  private readonly auth = inject(AuthService);

  readonly text = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(3072)],
  });
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    if (this.text.invalid) return;
    const content = this.text.value.trim();
    if (!content) return;
    this.submitting.set(true);
    this.error.set(null);
    const replyId = this.replyingTo ? this.replyingTo.id : null;
    this.chat.post(this.roomId, content, replyId).subscribe({
      next: () => {
        this.text.reset('');
        this.submitting.set(false);
        if (this.replyingTo) {
          this.clearReply.emit();
        }
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.error.set(this.auth.errorText(err));
      },
    });
  }

  handleKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.submit();
    }
  }
}
