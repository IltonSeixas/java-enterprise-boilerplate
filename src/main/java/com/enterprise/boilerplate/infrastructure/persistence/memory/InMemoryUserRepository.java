package com.enterprise.boilerplate.infrastructure.persistence.memory;

import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Repository
@Profile("inmemory")
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> store = new ConcurrentHashMap<>();
    private final ReentrantLock firstOwnerLock = new ReentrantLock();

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(store.get(id.toString()));
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return store.values().stream()
                .filter(u -> u.getEmail().equals(email))
                .findFirst();
    }

    @Override
    public void save(User user) {
        store.put(user.getId().toString(), user);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return store.values().stream().anyMatch(u -> u.getEmail().equals(email));
    }

    @Override
    public boolean hasOwner() {
        return store.values().stream().anyMatch(u -> u.getRole() == User.Role.OWNER);
    }

    @Override
    public void saveFirstOwner(User user) {
        firstOwnerLock.lock();
        try {
            if (hasOwner()) {
                throw new UserAlreadyExistsException("An owner already exists");
            }
            store.put(user.getId().toString(), user);
        } finally {
            firstOwnerLock.unlock();
        }
    }
}
