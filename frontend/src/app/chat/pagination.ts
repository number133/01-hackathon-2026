export const MESSAGE_PAGE_SIZE = 50;

export interface HistoryResponse<T> {
  items: T[];
  hasMore: boolean;
}
