package com.hackathon.chat.room;

import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final UserService userService;

    public RoomController(RoomService roomService, UserService userService) {
        this.roomService = roomService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<RoomView> create(@Valid @RequestBody CreateRoomRequest request) {
        User me = currentUser();
        Room room = roomService.create(me.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roomService.getForViewer(room.getId(), me.getId()));
    }

    @GetMapping("/{id}")
    public RoomView get(@PathVariable UUID id) {
        return roomService.getForViewer(id, currentUser().getId());
    }

    @PatchMapping("/{id}")
    public RoomView update(@PathVariable UUID id, @Valid @RequestBody UpdateRoomRequest patch) {
        User me = currentUser();
        roomService.update(id, me.getId(), patch);
        return roomService.getForViewer(id, me.getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        roomService.delete(id, currentUser().getId());
        return ResponseEntity.noContent().build();
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return userService.requireByUsername(auth.getName());
    }
}
