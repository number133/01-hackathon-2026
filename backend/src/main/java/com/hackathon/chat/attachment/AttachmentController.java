package com.hackathon.chat.attachment;

import com.hackathon.chat.common.RateLimiter;
import com.hackathon.chat.common.TooManyRequestsException;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserService;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService service;
    private final UserService userService;
    private final RateLimiter rateLimiter;

    public AttachmentController(AttachmentService service,
                                UserService userService,
                                RateLimiter rateLimiter) {
        this.service = service;
        this.userService = userService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public ResponseEntity<AttachmentView> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("conversationId") UUID conversationId,
            @RequestParam(value = "comment", required = false) String comment) throws IOException {
        UUID userId = me().getId();
        // Capacity 5 / refill 0.2 per second → ~12 uploads/min, 5 burst.
        if (!rateLimiter.tryAcquire("attachments.upload", userId, 5, 0.2)) {
            throw new TooManyRequestsException("attachments.upload");
        }
        AttachmentView view = service.upload(userId, conversationId, file, comment);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/{id}/metadata")
    public AttachmentView metadata(@PathVariable UUID id) {
        return service.view(id, me().getId());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileSystemResource> download(@PathVariable UUID id) throws IOException {
        AttachmentService.AttachmentDownload d = service.read(id, me().getId());
        if (!Files.exists(d.path())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(parseMime(d.mimeType()));
        headers.setContentLength(d.sizeBytes());
        String encoded = URLEncoder.encode(d.originalName(), StandardCharsets.UTF_8).replace("+", "%20");
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + safeAscii(d.originalName()) + "\"; filename*=UTF-8''" + encoded);
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(d.path()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) throws IOException {
        service.cancel(id, me().getId());
        return ResponseEntity.noContent().build();
    }

    private User me() {
        return userService.requireByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private static MediaType parseMime(String mime) {
        try {
            return MediaType.parseMediaType(mime);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String safeAscii(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (char c : name.toCharArray()) {
            if (c < 0x20 || c == '"' || c > 0x7e) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
