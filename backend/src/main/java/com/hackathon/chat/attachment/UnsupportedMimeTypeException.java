package com.hackathon.chat.attachment;

public class UnsupportedMimeTypeException extends RuntimeException {

    public UnsupportedMimeTypeException(String mime) {
        super("Mime type not supported: " + mime);
    }
}
