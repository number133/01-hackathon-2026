import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, computed } from '@angular/core';

import { PresenceDotComponent } from '../presence/presence-dot.component';
import { RoomMemberView, RoomView } from './room.service';

@Component({
  selector: 'app-room-context-pane',
  standalone: true,
  imports: [CommonModule, PresenceDotComponent],
  templateUrl: './room-context-pane.component.html',
})
export class RoomContextPaneComponent {
  @Input({ required: true }) room!: RoomView;
  @Input() members: RoomMemberView[] = [];
  @Output() readonly manageRequested = new EventEmitter<void>();

  get visibilityLabel(): string {
    return this.room.visibility === 'public' ? 'Public room' : 'Private room';
  }

  admins(): RoomMemberView[] {
    return this.members.filter((m) => m.role === 'admin' || m.role === 'owner');
  }

  nonAdminMembers(): RoomMemberView[] {
    return this.members.filter((m) => m.role === 'member');
  }

  canManage(): boolean {
    return this.room.myRole === 'owner' || this.room.myRole === 'admin';
  }
}
