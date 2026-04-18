package com.hackathon.chat.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");

        String dbState = "down";
        String flywayVersion = null;
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            dbState = "ok";
            flywayVersion = jdbc.query(
                    "SELECT version FROM flyway_schema_history "
                            + "WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null);
        } catch (DataAccessException ex) {
            response.put("status", "degraded");
            response.put("error", ex.getMostSpecificCause().getMessage());
        }

        response.put("db", dbState);
        response.put("flywayVersion", flywayVersion);
        return response;
    }
}
