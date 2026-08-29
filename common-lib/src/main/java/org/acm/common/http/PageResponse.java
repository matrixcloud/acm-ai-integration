package org.acm.common.http;

import java.util.Collections;
import java.util.List;

/**
 * Transport-neutral page envelope for list endpoints.
 *
 * @param items the page's items, never null
 * @param page pagination metadata, fully readable and constructible from outside this package
 */
public class PageResponse<T> {
    private final List<T> items;
    private final Page page;

    public PageResponse(List<T> items, Page page) {
        this.items = items == null ? Collections.emptyList() : List.copyOf(items);
        this.page = page;
    }

    public List<T> getItems() {
        return items;
    }

    public Page getPage() {
        return page;
    }

    /**
     * Pagination metadata. All fields have public accessors so Jackson serializes it and
     * adapters can construct it.
     */
    public static class Page {
        private final Integer number;
        private final Integer size;
        private final Long totalElements;
        private final Integer totalPages;

        public Page(Integer number, Integer size, Long totalElements, Integer totalPages) {
            this.number = number;
            this.size = size;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
        }

        public Integer getNumber() {
            return number;
        }

        public Integer getSize() {
            return size;
        }

        public Long getTotalElements() {
            return totalElements;
        }

        public Integer getTotalPages() {
            return totalPages;
        }
    }
}
