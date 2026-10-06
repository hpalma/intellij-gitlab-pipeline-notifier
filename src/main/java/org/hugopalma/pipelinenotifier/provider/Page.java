package org.hugopalma.pipelinenotifier.provider;

import java.util.List;

/**
 * One page of results. {@code hasMore} is reported by the client rather than inferred from the
 * size by the caller, because a client that filters client-side returns short pages that are not
 * the last one.
 */
public record Page<T>(List<T> items, boolean hasMore) {

    public static <T> Page<T> of(List<T> items, int requestedPerPage, int rawCount) {
        return new Page<>(items, rawCount >= requestedPerPage);
    }
}
