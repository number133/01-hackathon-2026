import { Component } from '@angular/core';

import { RoomsCatalogComponent } from './rooms-catalog.component';

@Component({
  selector: 'app-private-rooms-catalog',
  standalone: true,
  imports: [RoomsCatalogComponent],
  template: `<app-rooms-catalog mode="private"></app-rooms-catalog>`,
})
export class PrivateRoomsCatalogComponent {}
