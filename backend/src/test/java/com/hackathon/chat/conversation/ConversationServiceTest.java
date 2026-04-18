package com.hackathon.chat.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class ConversationServiceTest {

    private ConversationRepository repository;
    private JdbcTemplate jdbc;
    private ConversationService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ConversationRepository.class);
        jdbc = Mockito.mock(JdbcTemplate.class);
        service = new ConversationService(repository, jdbc);
    }

    @SuppressWarnings("unchecked")
    @Test
    void assignNextSeqReturnsMonotonicValues() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object.class)))
                .thenReturn(1L, 2L, 3L);

        assertThat(service.assignNextSeq(id)).isEqualTo(1L);
        assertThat(service.assignNextSeq(id)).isEqualTo(2L);
        assertThat(service.assignNextSeq(id)).isEqualTo(3L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void assignNextSeqThrowsWhenConversationMissing() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.assignNextSeq(id))
                .isInstanceOf(NoSuchElementException.class);
    }
}
