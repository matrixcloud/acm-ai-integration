package org.acm.common.http;

import java.util.Collections;
import java.util.List;

public class SearchRequest<F, S> {
  private List<F> filters;
  private List<S> sorts;
  private Long page;
  private Long size;

  public SearchRequest() {}

  public SearchRequest(List<F> filters, List<S> sorts, Long page, Long size) {
    this.filters = filters;
    this.sorts = sorts;
    this.page = page;
    this.size = size;
  }

  public List<F> getFilters() {
    if (filters == null || filters.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(filters);
  }

  public List<S> getSorts() {
    if (sorts == null || sorts.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(sorts);
  }

  public Long getPage() {
    if (page == null) {
      return 1L;
    }

    return page;
  }

  public Long getSize() {
    if (size == null) {
      return 0L;
    }

    return size;
  }
}
