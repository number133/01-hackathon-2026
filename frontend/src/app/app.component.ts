import { CommonModule } from '@angular/common';
import { Component, effect, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AuthService } from './auth/auth.service';
import { ChatService } from './chat/chat.service';
import { FriendService } from './contacts/friend.service';
import { TopMenuComponent } from './layout/top-menu.component';
import { PresenceService } from './presence/presence.service';
import { UnreadService } from './unread/unread.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, TopMenuComponent],
  templateUrl: './app.component.html',
})
export class AppComponent {
  private readonly auth = inject(AuthService);
  private readonly presence = inject(PresenceService);
  private readonly chat = inject(ChatService);
  private readonly friends = inject(FriendService);
  private readonly unread = inject(UnreadService);

  readonly ready = this.auth.ready;

  constructor() {
    effect(() => {
      if (this.auth.isAuthenticated()) {
        this.chat.connect();
        this.presence.start();
        this.friends.start();
        this.unread.start();
      }
    });
  }
}
