import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AuthService } from './auth/auth.service';
import { TopMenuComponent } from './layout/top-menu.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, TopMenuComponent],
  templateUrl: './app.component.html',
})
export class AppComponent {
  private readonly auth = inject(AuthService);

  readonly ready = this.auth.ready;
}
