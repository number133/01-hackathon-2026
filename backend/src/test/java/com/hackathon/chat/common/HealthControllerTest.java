package com.hackathon.chat.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private HealthController controller;

    @Test
    void returnsOkWhenDatabaseReachable() {
        when(jdbc.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class))).thenReturn("1");

        Map<String, Object> result = controller.health();

        assertThat(result)
                .containsEntry("status", "ok")
                .containsEntry("db", "ok")
                .containsEntry("flywayVersion", "1");
    }

    @Test
    void reportsDegradedWhenDatabaseUnreachable() {
        when(jdbc.queryForObject(eq("SELECT 1"), eq(Integer.class)))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        Map<String, Object> result = controller.health();

        assertThat(result)
                .containsEntry("status", "degraded")
                .containsEntry("db", "down")
                .containsEntry("flywayVersion", null);
        assertThat(result).containsKey("error");
    }
}
