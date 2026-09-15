package io.softa.framework.orm.domain;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.constant.BaseConstant;
import io.softa.framework.base.exception.IllegalArgumentException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Omitting a page size and stating an impossible one are different requests.
 *
 * <p>They used to be the same one: {@code pageSize == null || pageSize < 1} both fell through to 50.
 * So a caller that asked for no rows got a full page of them, and a caller whose size came out of a
 * computation — {@code ids.size()}, an off-by-one, a filter that matched nothing — got a plausible
 * looking response instead of being told the number was wrong. Nothing downstream could recover the
 * distinction afterwards, because the response echoes the page size the server chose.
 *
 * <p>{@link Page} is where this has to live rather than in one request DTO: every client-facing page
 * size reaches the constructor, and about half of them come from bare {@code @RequestParam}s on
 * flow-, es- and user-starter controllers rather than from {@code QueryParams}. Guarding the DTO
 * would have left those paths with the old behaviour, which is the asymmetry this removes.
 *
 * <p>The upper bound was already enforced. Only the lower one is new.
 */
class PageSizeBoundsTest {

    @Test
    void anOmittedPageSizeTakesTheDefault() {
        assertThat(Page.of(1, null).getPageSize()).isEqualTo(BaseConstant.DEFAULT_PAGE_SIZE);
    }

    @Test
    void zeroIsRejectedRatherThanTurnedIntoAFullPage() {
        assertThatThrownBy(() -> Page.of(1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void aNegativePageSizeIsRejected() {
        // Not clamped to 0 either: LIMIT -10 is a SQL error, and reading -10 as "none" is one more
        // guess about what the caller meant.
        assertThatThrownBy(() -> Page.of(1, -10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void aStatedPageSizeWithinTheBoundsIsHonoured() {
        assertThat(Page.of(2, 25).getPageSize()).isEqualTo(25);
        assertThat(Page.of(1, BaseConstant.MAX_BATCH_SIZE).getPageSize())
                .isEqualTo(BaseConstant.MAX_BATCH_SIZE);
    }

    @Test
    void theUpperBoundStillHolds() {
        assertThatThrownBy(() -> Page.of(1, BaseConstant.MAX_BATCH_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void cursorPagingIsBoundedTheSameWay() {
        // ofCursorPage ignores pageNumber but not the size — it is the batch size every internal
        // sweep reads with, and a zero there is an infinite do/while.
        assertThat(Page.ofCursorPage().getPageSize()).isEqualTo(BaseConstant.DEFAULT_PAGE_SIZE);
        assertThatThrownBy(() -> Page.ofCursorPage(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageNumberIsDeliberatelyLeftAlone() {
        // Out of scope, and stated so: this change is about the size parameter. A page number below 1
        // still falls back to the first page, as it always has.
        assertThat(Page.of(0, 10).getPageNumber()).isEqualTo(BaseConstant.DEFAULT_PAGE_NUMBER);
        assertThat(Page.of(-3, 10).getPageNumber()).isEqualTo(BaseConstant.DEFAULT_PAGE_NUMBER);
        assertThat(Page.of(null, 10).getPageNumber()).isEqualTo(BaseConstant.DEFAULT_PAGE_NUMBER);
    }
}
