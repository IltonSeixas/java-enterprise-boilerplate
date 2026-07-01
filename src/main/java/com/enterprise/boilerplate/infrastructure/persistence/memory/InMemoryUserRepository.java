package com.enterprise.boilerplate.infrastructure.persistence.memory;

import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.repository.PageCriteria;
import com.enterprise.boilerplate.domain.repository.UserFilter;
import com.enterprise.boilerplate.domain.repository.UserPage;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Repository
@Profile("inmemory")
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> store = new ConcurrentHashMap<>();
    private final ReentrantLock writeLock = new ReentrantLock();

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
        writeLock.lock();
        try {
            String currentId = user.getId().toString();
            boolean emailTaken = store.values().stream()
                    .anyMatch(u -> u.getEmail().equals(user.getEmail()) && !u.getId().toString().equals(currentId));
            if (emailTaken) {
                throw new UserAlreadyExistsException(user.getEmail().value());
            }
            store.put(currentId, user);
        } finally {
            writeLock.unlock();
        }
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
        writeLock.lock();
        try {
            if (hasOwner()) {
                throw UserAlreadyExistsException.ownerAlreadyExists();
            }
            store.put(user.getId().toString(), user);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public UserPage findAll(UserFilter filter, PageCriteria pageCriteria) {
        List<User> matched = store.values().stream()
                .filter(u -> filter.role() == null || u.getRole() == filter.role())
                .filter(u -> filter.active() == null || u.isActive() == filter.active())
                .filter(u -> filter.nameContains() == null
                        || u.getName().toLowerCase().contains(filter.nameContains().toLowerCase()))
                .sorted(Comparator.comparing(User::getCreatedAt))
                .toList();

        int from = Math.min(pageCriteria.page() * pageCriteria.size(), matched.size());
        int to = Math.min(from + pageCriteria.size(), matched.size());

        return new UserPage(matched.subList(from, to), matched.size());
    }
}
