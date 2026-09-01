package com.company.iss.shared.pagination;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * A {@link Pageable} that preserves Vaadin's exact offset and limit, including
 * windows whose offset is not aligned to the requested limit.
 */
public final class OffsetLimitPageable implements Pageable, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long offset;
    private final int limit;
    private final Sort sort;

    public OffsetLimitPageable(long offset, int limit, Sort sort) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative.");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be greater than zero.");
        }
        this.offset = offset;
        this.limit = limit;
        this.sort = Objects.requireNonNull(sort, "Sort is required.");
    }

    public OffsetLimitPageable(long offset, int limit) {
        this(offset, limit, Sort.unsorted());
    }

    @Override
    public int getPageNumber() {
        return Math.toIntExact(offset / limit);
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetLimitPageable(Math.addExact(offset, limit), limit, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious()
                ? new OffsetLimitPageable(Math.max(0, offset - limit), limit, sort)
                : first();
    }

    @Override
    public Pageable first() {
        return new OffsetLimitPageable(0, limit, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("Page index must not be negative.");
        }
        return new OffsetLimitPageable(Math.multiplyExact((long) pageNumber, limit), limit, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
