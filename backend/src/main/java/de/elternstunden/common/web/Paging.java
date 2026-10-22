package de.elternstunden.common.web;

import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import java.util.List;

/**
 * Paging of admin lists (8.1): {@code page} from 0, {@code size} default 50 and at most 200. The lists are small
 * (1.2), so they are filtered and cut in memory.
 */
public record Paging(int page, int size) {

    public static final int MAX_SIZE = 200;

    public static Paging of(Integer page, Integer size) {
        if (size > MAX_SIZE) {
            throw new ApiException(ErrorCode.PAGE_SIZE_EXCEEDED);
        }
        return new Paging(page, size);
    }

    public <T> List<T> slice(List<T> all) {
        long from = (long) page * size;
        if (from >= all.size()) {
            return List.of();
        }
        return all.subList((int) from, (int) Math.min(all.size(), from + size));
    }

    public int totalPages(int totalElements) {
        return (totalElements + size - 1) / size;
    }
}
