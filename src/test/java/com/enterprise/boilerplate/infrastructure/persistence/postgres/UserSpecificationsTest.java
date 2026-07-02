package com.enterprise.boilerplate.infrastructure.persistence.postgres;

import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.repository.UserFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class UserSpecificationsTest {

    @Mock private Root<UserJpaEntity> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder builder;
    @Mock private Predicate conjunction;
    @Mock private Predicate combined;
    @Mock private Path rolePath;
    @Mock private Path activePath;
    @Mock private Path namePath;
    @Mock private Expression<String> nameExpression;

    @BeforeEach
    void setUp() {
        when(builder.conjunction()).thenReturn(conjunction);
    }

    @Test
    void emptyFilter_returnsConjunction_withNoPedicatesAdded() {
        UserFilter filter = new UserFilter(null, null, null);

        Specification<UserJpaEntity> spec = UserSpecifications.matching(filter);
        spec.toPredicate(root, query, builder);

        verify(builder, never()).equal(any(), any(Object.class));
        verify(builder, never()).like(any(), anyString(), anyChar());
    }

    @Test
    void roleFilter_addsEqualPredicate() {
        UserFilter filter = new UserFilter(User.Role.ADMIN, null, null);

        when(root.get("role")).thenReturn(rolePath);
        when(builder.equal(rolePath, User.Role.ADMIN)).thenReturn(combined);
        when(builder.and(any(), any())).thenReturn(combined);

        Specification<UserJpaEntity> spec = UserSpecifications.matching(filter);
        spec.toPredicate(root, query, builder);

        verify(builder).equal(rolePath, User.Role.ADMIN);
    }

    @Test
    void activeFilter_addsEqualPredicate() {
        UserFilter filter = new UserFilter(null, true, null);

        when(root.get("active")).thenReturn(activePath);
        when(builder.equal(activePath, true)).thenReturn(combined);
        when(builder.and(any(), any())).thenReturn(combined);

        Specification<UserJpaEntity> spec = UserSpecifications.matching(filter);
        spec.toPredicate(root, query, builder);

        verify(builder).equal(activePath, true);
    }

    @Test
    void nameContainsFilter_addsLikePredicate() {
        UserFilter filter = new UserFilter(null, null, "alice");

        when(root.get("name")).thenReturn(namePath);
        when(builder.lower(namePath)).thenReturn(nameExpression);
        when(builder.like(eq(nameExpression), anyString(), eq('\\'))).thenReturn(combined);
        when(builder.and(any(), any())).thenReturn(combined);

        Specification<UserJpaEntity> spec = UserSpecifications.matching(filter);
        spec.toPredicate(root, query, builder);

        verify(builder).like(eq(nameExpression), eq("%alice%"), eq('\\'));
    }

    @Test
    void nameContainsFilter_escapesPercentAndUnderscore() {
        // % and _ in search term must be escaped so they match literally.
        UserFilter filter = new UserFilter(null, null, "100%_done");

        when(root.get("name")).thenReturn(namePath);
        when(builder.lower(namePath)).thenReturn(nameExpression);
        when(builder.like(eq(nameExpression), anyString(), eq('\\'))).thenReturn(combined);
        when(builder.and(any(), any())).thenReturn(combined);

        Specification<UserJpaEntity> spec = UserSpecifications.matching(filter);
        spec.toPredicate(root, query, builder);

        verify(builder).like(eq(nameExpression), eq("%100\\%\\_done%"), eq('\\'));
    }

    @Test
    void blankNameContainsFilter_isIgnored() {
        UserFilter filter = new UserFilter(null, null, "   ");

        Specification<UserJpaEntity> spec = UserSpecifications.matching(filter);
        spec.toPredicate(root, query, builder);

        verify(builder, never()).like(any(), anyString(), anyChar());
    }
}
