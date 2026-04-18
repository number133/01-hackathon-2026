package com.hackathon.chat.attachment;

import java.util.UUID;

public record AttachmentRef(
        UUID id,
        String originalName,
        String mimeType,
        long sizeBytes,
        String comment,
        boolean isImage) {

    public static AttachmentRef of(Attachment a) {
        return new AttachmentRef(
                a.getId(),
                a.getOriginalName(),
                a.getMimeType(),
                a.getSizeBytes(),
                a.getComment(),
                a.isImage());
    }
}
