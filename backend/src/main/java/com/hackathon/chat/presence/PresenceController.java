package com.hackathon.chat.presence;

import com.hackathon.chat.common.AccountConflictException;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private static final int MAX_BULK_IDS = 200;

    private final PresenceService service;
    private final PresenceProperties props;
    private final UserService userService;

    public PresenceController(PresenceService service,
                              PresenceProperties props,
                              UserService userService) {
        this.service = service;
        this.props = props;
        this.userService = userService;
    }

    @PostMapping("/ping")
    public ResponseEntity<Void> ping(@Valid @RequestBody PresencePingRequest request) {
        service.recordPing(me().getId(), request.tabId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<PresenceView> bulk(@RequestParam("userIds") List<UUID> userIds) {
        if (userIds.size() > MAX_BULK_IDS) {
            throw new AccountConflictException("too_many_ids");
        }
        List<UUID> dedup = new ArrayList<>(new java.util.LinkedHashSet<>(userIds));
        return service.bulkStatus(dedup);
    }

    @GetMapping("/config")
    public PresenceConfigView config() {
        return new PresenceConfigView(props.pingInterval().toMillis());
    }

    private User me() {
        return userService.requireByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
