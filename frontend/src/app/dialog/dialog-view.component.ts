import { CommonModule } from '@angular/common';
import { Component, ViewChild, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AttachmentPickerComponent } from '../attachment/attachment-picker.component';
import { AttachmentViewComponent } from '../attachment/attachment-view.component';
import { AuthService } from '../auth/auth.service';
import { ChatService } from '../chat/chat.service';
import { PresenceDotComponent } from '../presence/presence-dot.component';
import { PresenceService } from '../presence/presence.service';
import { DialogService, DialogView } from './dialog.service';

@Component({
  selector: 'app-dialog-view',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    PresenceDotComponent,
    AttachmentPickerComponent,
    AttachmentViewComponent,
  ],
  templateUrl: './dialog-view.component.html',
})
export class DialogViewComponent {
  @ViewChild(AttachmentPickerComponent) picker?: AttachmentPickerComponent;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialogs = inject(DialogService);
  private readonly chat = inject(ChatService);
  private readonly auth = inject(AuthService);
  private readonly presence = inject(PresenceService);

  readonly dialog = signal<DialogView | null>(null);
  readonly error = signal<string | null>(null);
  readonly attachmentIds = signal<string[]>([]);
  readonly text = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.maxLength(3072)],
  });

  readonly messages = computed(() => {
    const d = this.dialog();
    return d ? this.dialogs.state(d.id).messages : [];
  });

  private dialogId: string | null = null;
  private watchedCounterpart: string | null = null;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.router.navigate(['/dialogs']);
      return;
    }
    this.dialogId = id;
    this.dialogs.get(id).subscribe({
      next: (d) => {
        this.dialog.set(d);
        this.chat.connect();
        this.dialogs.subscribeDialog(id);
        this.dialogs.loadInitial(id).subscribe();
        this.watchedCounterpart = d.counterpartId;
        this.presence.watch([d.counterpartId]);
      },
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  ngOnDestroy(): void {
    if (this.dialogId) this.dialogs.unsubscribeDialog(this.dialogId);
    if (this.watchedCounterpart) this.presence.unwatch([this.watchedCounterpart]);
  }

  onAttachmentsChange(ids: string[]): void {
    this.attachmentIds.set(ids);
  }

  canSend(): boolean {
    const d = this.dialog();
    if (!d || d.frozen) return false;
    const hasText = this.text.value.trim().length > 0;
    return hasText || this.attachmentIds().length > 0;
  }

  send(): void {
    const d = this.dialog();
    if (!d || d.frozen || !this.canSend()) return;
    const body = this.text.value.trim();
    const ids = this.attachmentIds();
    this.dialogs.post(d.id, body, null, ids).subscribe({
      next: () => {
        this.text.reset('');
        this.picker?.clear();
        this.attachmentIds.set([]);
      },
      error: (err: unknown) => this.error.set(this.auth.errorText(err)),
    });
  }

  handleKeydown(ev: KeyboardEvent): void {
    if (ev.key === 'Enter' && !ev.shiftKey) {
      ev.preventDefault();
      this.send();
    }
  }
}
