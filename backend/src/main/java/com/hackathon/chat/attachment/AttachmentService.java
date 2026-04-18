package com.hackathon.chat.attachment;

import com.hackathon.chat.common.AccountConflictException;
import com.hackathon.chat.common.ForbiddenException;
import com.hackathon.chat.conversation.Conversation;
import com.hackathon.chat.conversation.ConversationService;
import com.hackathon.chat.dialog.DialogService;
import com.hackathon.chat.room.Room;
import com.hackathon.chat.room.RoomBanRepository;
import com.hackathon.chat.room.RoomRepository;
import com.hackathon.chat.room.RoomService;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository repository;
    private final AttachmentProperties props;
    private final ConversationService conversationService;
    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final RoomBanRepository roomBanRepository;
    private final DialogService dialogService;
    private final UserRepository userRepository;
    private final Clock clock;

    public AttachmentService(AttachmentRepository repository,
                             AttachmentProperties props,
                             ConversationService conversationService,
                             RoomService roomService,
                             RoomRepository roomRepository,
                             RoomBanRepository roomBanRepository,
                             DialogService dialogService,
                             UserRepository userRepository,
                             Clock clock) {
        this.repository = repository;
        this.props = props;
        this.conversationService = conversationService;
        this.roomService = roomService;
        this.roomRepository = roomRepository;
        this.roomBanRepository = roomBanRepository;
        this.dialogService = dialogService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public AttachmentView upload(UUID uploaderId,
                                 UUID conversationId,
                                 MultipartFile file,
                                 String comment) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        String mime = file.getContentType();
        if (mime == null || mime.isBlank()) {
            mime = "application/octet-stream";
        }
        if (isBlocked(mime)) {
            throw new UnsupportedMimeTypeException(mime);
        }
        long size = file.getSize();
        boolean isImage = mime.startsWith("image/");
        long cap = isImage ? props.maxImageSize().toBytes() : props.maxSize().toBytes();
        if (size > cap) {
            throw new AttachmentTooLargeException(size, cap);
        }
        assertParticipant(conversationId, uploaderId);

        String originalName = sanitizeName(file.getOriginalFilename());
        String ext = extensionOf(originalName);
        UUID storedId = UUID.randomUUID();
        Path dir = storageRoot().resolve(conversationId.toString());
        Files.createDirectories(dir);
        Path finalPath = dir.resolve(storedId + ext);
        Path partPath = dir.resolve(storedId + ext + ".part");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, partPath, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(partPath, finalPath, StandardCopyOption.ATOMIC_MOVE);

        Attachment saved = repository.save(new Attachment(
                conversationId, uploaderId,
                finalPath.toString(), originalName, mime, size, trimOrNull(comment)));
        return toView(saved);
    }

    public void linkToMessage(Collection<UUID> attachmentIds,
                              UUID conversationId,
                              UUID uploaderId,
                              UUID messageId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        if (attachmentIds.size() > props.maxPerMessage()) {
            throw new AccountConflictException("Too many attachments");
        }
        for (UUID id : attachmentIds) {
            Attachment a = repository.findById(id)
                    .orElseThrow(() -> new AccountConflictException("Attachment not found"));
            if (a.getMessageId() != null) {
                throw new AccountConflictException("Attachment already linked");
            }
            if (a.getUploaderId() == null || !a.getUploaderId().equals(uploaderId)) {
                throw new AccountConflictException("Attachment uploader mismatch");
            }
            if (!a.getConversationId().equals(conversationId)) {
                throw new AccountConflictException("Attachment conversation mismatch");
            }
            a.linkTo(messageId);
        }
    }

    public void cancel(UUID attachmentId, UUID callerId) throws IOException {
        Attachment a = requireById(attachmentId);
        if (a.getUploaderId() == null || !a.getUploaderId().equals(callerId)) {
            throw new ForbiddenException("Not the uploader");
        }
        if (a.getMessageId() != null) {
            throw new AccountConflictException("Already linked to a message");
        }
        Files.deleteIfExists(Path.of(a.getStoredPath()));
        repository.delete(a);
    }

    @Transactional(readOnly = true)
    public AttachmentView view(UUID attachmentId, UUID callerId) {
        Attachment a = requireById(attachmentId);
        assertCanRead(a, callerId);
        return toView(a);
    }

    @Transactional(readOnly = true)
    public AttachmentDownload read(UUID attachmentId, UUID callerId) {
        Attachment a = requireById(attachmentId);
        assertCanRead(a, callerId);
        return new AttachmentDownload(
                Path.of(a.getStoredPath()),
                a.getMimeType(),
                a.getOriginalName(),
                a.getSizeBytes());
    }

    public int sweepOrphans() {
        Instant cutoff = clock.instant().minus(props.orphanTtl());
        List<Attachment> orphans = repository.findOrphansOlderThan(cutoff);
        for (Attachment a : orphans) {
            try {
                Files.deleteIfExists(Path.of(a.getStoredPath()));
            } catch (IOException io) {
                log.warn("orphan file delete failed: {} ({})", a.getStoredPath(), io.getMessage());
            }
            repository.delete(a);
        }
        return orphans.size();
    }

    public void deleteConversationTree(UUID conversationId) {
        Path dir = storageRoot().resolve(conversationId.toString());
        deleteTree(dir);
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<AttachmentRef>> refsByMessage(Collection<UUID> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        List<Attachment> rows = repository.findAllByMessageIdIn(messageIds);
        Map<UUID, List<AttachmentRef>> out = new HashMap<>();
        for (Attachment a : rows) {
            out.computeIfAbsent(a.getMessageId(), k -> new ArrayList<>())
                    .add(AttachmentRef.of(a));
        }
        return out;
    }

    private Attachment requireById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Attachment not found"));
    }

    private void assertCanRead(Attachment a, UUID callerId) {
        if (a.getMessageId() == null) {
            if (a.getUploaderId() == null || !a.getUploaderId().equals(callerId)) {
                throw new ForbiddenException("Attachment not readable");
            }
            return;
        }
        assertParticipant(a.getConversationId(), callerId);
    }

    private void assertParticipant(UUID conversationId, UUID userId) {
        Conversation conv = conversationService.require(conversationId);
        if (Conversation.TYPE_DIALOG.equals(conv.getType())) {
            if (!dialogService.isParticipant(conversationId, userId)) {
                throw new ForbiddenException("Not a dialog participant");
            }
            return;
        }
        Room room = roomRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new NoSuchElementException("Room not found"));
        if (roomBanRepository.existsByRoomIdAndUserId(room.getId(), userId)) {
            throw new ForbiddenException("Banned from this room");
        }
        roomService.requireMember(room.getId(), userId);
    }

    private AttachmentView toView(Attachment a) {
        String uploaderUsername = null;
        if (a.getUploaderId() != null) {
            uploaderUsername = userRepository.findById(a.getUploaderId())
                    .map(User::getUsername).orElse(null);
        }
        return new AttachmentView(
                a.getId(), a.getMessageId(), a.getConversationId(), a.getUploaderId(),
                uploaderUsername,
                a.getOriginalName(), a.getMimeType(), a.getSizeBytes(), a.getComment(),
                a.isImage(), a.getCreatedAt());
    }

    private boolean isBlocked(String mime) {
        List<String> blocked = props.blockedMimePrefixes();
        if (blocked == null) return false;
        for (String prefix : blocked) {
            if (mime.toLowerCase().startsWith(prefix.toLowerCase())) return true;
        }
        return false;
    }

    private Path storageRoot() {
        return Paths.get(props.storageRoot());
    }

    private static String sanitizeName(String original) {
        if (original == null || original.isBlank()) return "file";
        String base = original;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        if (base.length() > 255) base = base.substring(0, 255);
        return base.isBlank() ? "file" : base;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        String ext = name.substring(dot).toLowerCase();
        return ext.length() > 16 ? "" : ext;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) return;
        try {
            Files.walk(root)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException io) {
                            log.warn("delete failed: {} ({})", p, io.getMessage());
                        }
                    });
        } catch (IOException io) {
            log.warn("walk failed at {} ({})", root, io.getMessage());
        }
    }

    public record AttachmentDownload(Path path, String mimeType, String originalName, long sizeBytes) {
    }
}
