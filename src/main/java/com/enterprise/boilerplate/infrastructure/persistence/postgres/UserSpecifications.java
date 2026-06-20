package com.enterprise.boilerplate.infrastructure.persistence.postgres;

import com.enterprise.boilerplate.domain.repository.UserFilter;
import org.springframework.data.jpa.domain.Specification;

final class UserSpecifications {

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
                predicate = builder.and(predicate,
                        builder.like(builder.lower(root.get("name")), "%" + filter.nameContains().toLowerCase() + "%"));
            }

            return predicate;
        };
    }
}
