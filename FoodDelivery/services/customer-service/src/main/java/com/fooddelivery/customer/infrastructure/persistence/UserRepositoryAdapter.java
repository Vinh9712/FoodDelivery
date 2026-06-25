package com.fooddelivery.customer.infrastructure.persistence;

import com.fooddelivery.customer.domain.model.User;
import com.fooddelivery.customer.domain.model.enums.UserRole;
import com.fooddelivery.customer.domain.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJPARepository userJPARepository;

    public UserRepositoryAdapter(UserJPARepository userJPARepository) {
        this.userJPARepository = userJPARepository;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJPARepository.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJPARepository.existsByEmail(email);
    }

    @Override
    public boolean existsByPhone(String phone) {
        return userJPARepository.existsByPhone(phone);
    }

    @Override
    public User save(User user) {
        return userJPARepository.save(user);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJPARepository.findById(id);
    }

    @Override
    public List<User> findAll(int page, int size, String search, UserRole role, Boolean active) {
        Specification<User> spec = buildFilter(search, role, active);
        return userJPARepository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    @Override
    public long count(String search, UserRole role, Boolean active) {
        Specification<User> spec = buildFilter(search, role, active);
        return userJPARepository.count(spec);
    }

    @Override
    public long countByRole(UserRole role) {
        return userJPARepository.countByRole(role);
    }

    private Specification<User> buildFilter(String search, UserRole role, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isFalse(root.get("deleted")));

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("phone")), pattern)
                ));
            }

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
