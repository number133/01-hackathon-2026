package com.hackathon.chat.attachment;

public class AttachmentTooLargeException extends RuntimeException {

    private final long sizeBytes;
    private final long capBytes;

    public AttachmentTooLargeException(long sizeBytes, long capBytes) {
        super("File size " + sizeBytes + " exceeds cap " + capBytes);
        this.sizeBytes = sizeBytes;
        this.capBytes = capBytes;
    }

    public long getSizeBytes() { return sizeBytes; }
    public long getCapBytes() { return capBytes; }
}
