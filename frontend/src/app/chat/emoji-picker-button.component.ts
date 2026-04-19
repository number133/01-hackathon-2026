import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Output,
  inject,
  signal,
} from '@angular/core';
import { PickerComponent } from '@ctrl/ngx-emoji-mart';

@Component({
  selector: 'app-emoji-picker-button',
  standalone: true,
  imports: [CommonModule, PickerComponent],
  templateUrl: './emoji-picker-button.component.html',
})
export class EmojiPickerButtonComponent {
  @Output() emojiSelected = new EventEmitter<string>();

  readonly open = signal(false);

  private readonly host = inject(ElementRef<HTMLElement>);

  toggle(): void {
    this.open.update((v) => !v);
  }

  close(): void {
    this.open.set(false);
  }

  onEmojiSelect(event: { emoji?: { native?: string } } | null): void {
    const native = event?.emoji?.native;
    if (native) {
      this.emojiSelected.emit(native);
      this.close();
    }
  }

  @HostListener('document:mousedown', ['$event'])
  onDocumentMousedown(event: MouseEvent): void {
    if (!this.open()) return;
    const target = event.target as Node | null;
    if (target && this.host.nativeElement.contains(target)) return;
    this.close();
  }
}
