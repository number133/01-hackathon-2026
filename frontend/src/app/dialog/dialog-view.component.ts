import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  ViewChild,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AttachmentPickerComponent } from '../attachment/attachment-picker.component';
import { AuthService } from '../auth/auth.service';
import { ChatService, MessageView } from '../chat/chat.service';
import { EmojiPickerButtonComponent } from '../chat/emoji-picker-button.component';
import { MessageItemComponent } from '../chat/message-item.component';
import { ChatSidebarComponent } from '../layout/chat-sidebar.component';
import { PresenceDotComponent } from '../presence/presence-dot.component';
import { PresenceService } from '../presence/presence.service';
import { UnreadService } from '../unread/unread.service';
import { DialogService, DialogView } from './dialog.service';

@Component({
  selector: 'app-dialog-view',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    PresenceDotComponent,
    AttachmentPickerComponent,
    ChatSidebarComponent,
    MessageItemComponent,
    EmojiPickerButtonComponent,
  ],
  templateUrl: './dialog-view.component.html',
})
export class DialogViewComponent {
  @ViewChild(AttachmentPickerComponent) picker?: AttachmentPickerComponent;
  @ViewChild('scroller', { static: false }) scroller?: ElementRef<HTMLDivElement>;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialogs = inject(DialogService);
  private readonly chat = inject(ChatService);
  private readonly auth = inject(AuthService);
  private readonly presence = inject(PresenceService);
  private readonly unread = inject(UnreadService);

  readonly dialog = signal<DialogView | null>(null);
  readonly error = signal<string | null>(null);
  readonly attachmentIds = signal<string[]>([]);
  readonly replyingTo = signal<MessageView | null>(null);
  readonly text = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.maxLength(3072)],
  });
  readonly myUserId = computed(() => this.auth.user()?.id ?? null);

  readonly messages = computed(() => {
    const d = this.dialog();
    return d ? this.dialogs.state(d.id).messages : [];
  });
  readonly loading = computed(() => {
    const d = this.dialog();
    return d ? this.dialogs.state(d.id).loading : false;
  });
  readonly highestSeq = computed(() => {
    const d = this.dialog();
    return d ? this.dialogs.state(d.id).highestSeq : 0;
  });

  private dialogId: string | null = null;
  private watchedCounterpart: string | null = null;
  private lastAckedSeq = 0;
  private pinnedToBottom = true;
  private loadMoreGuard = false;

  constructor() {
    effect(() => {
      const seq = this.highestSeq();
      const d = this.dialog();
      if (d && seq > 0 && seq > this.lastAckedSeq) {
        this.lastAckedSeq = seq;
        this.unread.markRead(d.id, seq);
      }
    }, { allowSignalWrites: true });
    effect(() => {
      // Touch signals so the effect re-runs; only scroll if pinned.
      this.messages();
      this.highestSeq();
      if (!this.pinnedToBottom) return;
      requestAnimationFrame(() => this.scrollToBottom());
    });
  }

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

  trackByMessage(_index: number, m: MessageView): string {
    return m.id;
  }

  onScroll(): void {
    if (!this.scroller || !this.dialogId) return;
    const el = this.scroller.nativeElement;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    this.pinnedToBottom = distanceFromBottom < 40;
    if (el.scrollTop < 80 && !this.loadMoreGuard) {
      this.loadMoreGuard = true;
      const previousHeight = el.scrollHeight;
      const id = this.dialogId;
      this.dialogs.loadMore(id).subscribe({
        next: () => {
          this.loadMoreGuard = false;
          if (this.scroller) {
            const delta = this.scroller.nativeElement.scrollHeight - previousHeight;
            this.scroller.nativeElement.scrollTop = delta;
          }
        },
        error: () => {
          this.loadMoreGuard = false;
        },
      });
    }
  }

  private scrollToBottom(): void {
    if (!this.scroller) return;
    const el = this.scroller.nativeElement;
    el.scrollTop = el.scrollHeight;
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
    const replyId = this.replyingTo()?.id ?? null;
    this.dialogs.post(d.id, body, replyId, ids).subscribe({
      next: () => {
        this.text.reset('');
        this.picker?.clear();
        this.attachmentIds.set([]);
        this.replyingTo.set(null);
        this.pinnedToBottom = true;
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

  onReply(m: MessageView): void {
    this.replyingTo.set(m);
  }

  clearReply(): void {
    this.replyingTo.set(null);
  }

  canEdit(m: MessageView): boolean {
    const uid = this.myUserId();
    return !m.deletedAt && !!uid && m.authorId === uid;
  }

  canDelete(m: MessageView): boolean {
    if (m.deletedAt) return false;
    const uid = this.myUserId();
    return !!uid && m.authorId === uid;
  }

  insertEmoji(emoji: string): void {
    const next = (this.text.value ?? '') + emoji;
    if (next.length > 3072) return;
    this.text.setValue(next);
  }
}
