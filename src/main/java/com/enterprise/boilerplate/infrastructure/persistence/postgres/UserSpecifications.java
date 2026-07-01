package com.enterprise.boilerplate.infrastructure.persistence.postgres;

import com.enterprise.boilerplate.domain.repository.UserFilter;
import org.springframework.data.jpa.domain.Specification;

final class UserSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private UserSpecifications() {}

    static Specification<UserJpaEntity> matching(UserFilter filter) {
        return (root, query, builder) -> {
            var predicate = builder.conjunction();

            if (filter.role() != null) {
                predicate = builder.and(predicate, builder.equal(root.get("role"), filter.role()));
            }
            if (filter.active() != null) {
                predicate = builder.and(predicate, builder.equal(root.get("active"), filter.active()));
            }
            if (filter.nameContains() != null && !filter.nameContains().isBlank()) {
                String escaped = escapeLike(filter.nameContains().toLowerCase());
                predicate = builder.and(predicate,
                        builder.like(builder.lower(root.get("name")), "%" + escaped + "%", LIKE_ESCAPE));
            }

            return predicate;
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
