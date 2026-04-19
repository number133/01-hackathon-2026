package com.hackathon.chat.message;

import java.util.List;

public record HistoryPage(List<MessageView> items, boolean hasMore) {
}
