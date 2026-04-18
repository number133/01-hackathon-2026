import { CommonModule } from '@angular/common';
import {
  AfterViewChecked,
  Component,
  ElementRef,
  Input,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';

import { ChatService, MessageView } from './chat.service';
import { MessageItemComponent } from './message-item.component';

@Component({
  selector: 'app-message-list',
  standalone: true,
  imports: [CommonModule, MessageItemComponent],
  templateUrl: './message-list.component.html',
})
export class MessageListComponent implements OnInit, OnDestroy, AfterViewChecked {
  @Input({ required: true }) roomId!: string;
  @Input() myUserId: string | null = null;
  @Input() myRole: string | null = null;

  @ViewChild('scroller', { static: false }) scroller?: ElementRef<HTMLDivElement>;

  private readonly chat = inject(ChatService);
  private pinnedToBottom = true;
  private loadMoreGuard = false;

  readonly messages = computed(() => this.chat.state(this.roomId).messages);
  readonly loading = computed(() => this.chat.state(this.roomId).loading);
  readonly replyingTo = signal<MessageView | null>(null);

  ngOnInit(): void {
    this.chat.connect();
    this.chat.subscribeRoom(this.roomId);
    this.chat.loadInitial(this.roomId).subscribe();
  }

  ngOnDestroy(): void {
    this.chat.unsubscribeRoom(this.roomId);
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
