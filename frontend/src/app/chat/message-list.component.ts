import { CommonModule } from '@angular/common';
import {
  AfterViewChecked,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';

import { UnreadService } from '../unread/unread.service';
import { ChatService, MessageView } from './chat.service';
import { MessageItemComponent } from './message-item.component';

@Component({
  selector: 'app-message-list',
  standalone: true,
  imports: [CommonModule, MessageItemComponent],
  templateUrl: './message-list.component.html',
})
export class MessageListComponent implements OnChanges, OnDestroy, AfterViewChecked {
  @Input({ required: true }) roomId!: string;
  @Input({ required: true }) conversationId!: string;
  @Input() myUserId: string | null = null;
  @Input() myRole: string | null = null;

  @ViewChild('scroller', { static: false }) scroller?: ElementRef<HTMLDivElement>;

  private readonly chat = inject(ChatService);
  private readonly unread = inject(UnreadService);
  private pinnedToBottom = true;
  private loadMoreGuard = false;
  private lastAckedSeq = 0;
  private subscribedRoomId: string | null = null;

  readonly messages = computed(() => this.chat.state(this.roomId).messages);
  readonly loading = computed(() => this.chat.state(this.roomId).loading);
  readonly highestSeq = computed(() => this.chat.state(this.roomId).highestSeq);
  readonly replyingTo = signal<MessageView | null>(null);

  constructor() {
    effect(() => {
      const seq = this.highestSeq();
      if (seq > 0 && seq > this.lastAckedSeq && this.conversationId) {
        this.lastAckedSeq = seq;
        this.unread.markRead(this.conversationId, seq);
      }
    }, { allowSignalWrites: true });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['roomId']) return;
    this.chat.connect();
    if (this.subscribedRoomId && this.subscribedRoomId !== this.roomId) {
      this.chat.unsubscribeRoom(this.subscribedRoomId);
    }
    if (this.subscribedRoomId !== this.roomId) {
      this.chat.subscribeRoom(this.roomId);
      this.subscribedRoomId = this.roomId;
      this.replyingTo.set(null);
      this.lastAckedSeq = 0;
      this.pinnedToBottom = true;
      this.chat.loadInitial(this.roomId).subscribe();
    }
  }

  ngOnDestroy(): void {
    if (this.subscribedRoomId) {
      this.chat.unsubscribeRoom(this.subscribedRoomId);
      this.subscribedRoomId = null;
    }
  }

  ngAfterViewChecked(): void {
    if (!this.scroller) return;
    if (this.pinnedToBottom) {
      const el = this.scroller.nativeElement;
      el.scrollTop = el.scrollHeight;
    }
  }

  onScroll(): void {
    if (!this.scroller) return;
    const el = this.scroller.nativeElement;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    this.pinnedToBottom = distanceFromBottom < 40;
    if (el.scrollTop < 80 && !this.loadMoreGuard) {
      this.loadMoreGuard = true;
      const previousHeight = el.scrollHeight;
      this.chat.loadMore(this.roomId).subscribe({
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

  onReply(m: MessageView): void {
    this.replyingTo.set(m);
  }

  clearReply(): void {
    this.replyingTo.set(null);
  }

  canEdit(m: MessageView): boolean {
    return !m.deletedAt && !!this.myUserId && m.authorId === this.myUserId;
  }

  canDelete(m: MessageView): boolean {
    if (m.deletedAt) return false;
    if (this.myUserId && m.authorId === this.myUserId) return true;
    return this.myRole === 'admin' || this.myRole === 'owner';
  }
}
