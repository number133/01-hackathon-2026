package com.hackathon.chat.room;

import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserService;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomCatalogController {

    private final RoomService roomService;
    private final UserService userService;

    public RoomCatalogController(RoomService roomService, UserService userService) {
        this.roomService = roomService;
        this.userService = userService;
    }

    @GetMapping
    public List<RoomView> catalog(@RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "50") int limit) {
        User me = userService.requireByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
        return roomService.listPublicCatalog(q, limit, me.getId());
    }

    @GetMapping("/mine")
    public List<RoomView> mine() {
        User me = userService.requireByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
        return roomService.listMine(me.getId());
    }
}
