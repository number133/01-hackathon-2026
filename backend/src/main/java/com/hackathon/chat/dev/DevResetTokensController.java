package com.hackathon.chat.dev;

import com.hackathon.chat.auth.PasswordResetService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class DevResetTokensController {

    private final PasswordResetService passwordResetService;

    public DevResetTokensController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/password-reset-tokens")
    public List<PasswordResetService.RecentToken> recent() {
        return passwordResetService.recentTokens();
    }
}
