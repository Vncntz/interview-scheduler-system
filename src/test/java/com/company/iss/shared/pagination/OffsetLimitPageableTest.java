package com.company.iss.shared.pagination;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffsetLimitPageableTest {

    @Test
    void preservesNonAlignedOffsetLimitAndSort() {
        Sort sort = Sort.by(Sort.Order.desc("evaluationDate"));
        OffsetLimitPageable pageable = new OffsetLimitPageable(25, 10, sort);

        assertEquals(25, pageable.getOffset());
        assertEquals(10, pageable.getPageSize());
        assertEquals(2, pageable.getPageNumber());
        assertEquals(sort, pageable.getSort());
        assertTrue(pageable.hasPrevious());
        assertEquals(35, pageable.next().getOffset());
        assertEquals(15, pageable.previousOrFirst().getOffset());
        assertEquals(0, pageable.first().getOffset());
        assertEquals(40, pageable.withPage(4).getOffset());
    }

    @Test
    void validatesBoundsAndFirstPageNavigation() {
        OffsetLimitPageable first = new OffsetLimitPageable(0, 25);

        assertFalse(first.hasPrevious());
        assertEquals(0, first.previousOrFirst().getOffset());
        assertThrows(IllegalArgumentException.class, () -> new OffsetLimitPageable(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> new OffsetLimitPageable(0, 0));
        assertThrows(IllegalArgumentException.class, () -> first.withPage(-1));
    }
}
