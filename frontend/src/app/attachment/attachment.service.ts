import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface AttachmentView {
  id: string;
  messageId: string | null;
  conversationId: string;
  uploaderId: string | null;
  uploaderUsername: string | null;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
  comment: string | null;
  isImage: boolean;
  createdAt: string;
}

export interface AttachmentRef {
  id: string;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
  comment: string | null;
  isImage: boolean;
}

@Injectable({ providedIn: 'root' })
export class AttachmentService {
  private readonly http = inject(HttpClient);

  upload(file: File, conversationId: string, comment: string | null): Observable<AttachmentView> {
    const form = new FormData();
    form.append('file', file);
    form.append('conversationId', conversationId);
    if (comment && comment.trim().length > 0) {
      form.append('comment', comment.trim());
    }
    return this.http.post<AttachmentView>('/api/attachments', form);
  }

  cancel(id: string): Observable<void> {
    return this.http.delete<void>(`/api/attachments/${id}`);
  }

  downloadUrl(id: string): string {
    return `/api/attachments/${id}`;
  }
}
