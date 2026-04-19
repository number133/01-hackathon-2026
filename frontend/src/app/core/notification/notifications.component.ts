import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';

import { NotificationService } from './notification.service';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notifications.component.html',
})
export class NotificationsComponent {
  private readonly notifications = inject(NotificationService);
  readonly items = this.notifications.list;

  dismiss(id: number): void {
    this.notifications.dismiss(id);
  }
}
