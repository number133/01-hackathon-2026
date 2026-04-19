package com.hackathon.chat.dev;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "chat.demo.seed-enabled", havingValue = "true")
public class DemoAccountsController {

    @GetMapping("/demo-accounts")
    public List<DemoAccount> list() {
        return DemoDataSeeder.SEED_ACCOUNTS;
    }
}
