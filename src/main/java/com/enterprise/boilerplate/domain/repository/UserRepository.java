package com.enterprise.boilerplate.domain.repository;

import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.UserId;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    void save(User user);

    boolean existsByEmail(Email email);

    boolean hasOwner();

    void saveFirstOwner(User user);

    UserPage findAll(UserFilter filter, PageCriteria pageCriteria);
}
