package com.fooddelivery.authentication.infrastructure.persistence;

import com.fooddelivery.authentication.domain.model.User;
import com.fooddelivery.authentication.domain.model.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(UserRepositoryAdapter.class)
class UserRepositoryAdapterIntegrationTest {

    @Autowired
    private UserRepositoryAdapter userRepositoryAdapter;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findAllAndCount_excludeSoftDeletedUsers() {
        User activeUser = persist("active@example.com", "0900000001");
        User deletedUser = persist("deleted@example.com", "0900000002");
        deletedUser.softDelete();
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepositoryAdapter.findAll(0, 10, null, null, null))
                .extracting(User::getId)
                .containsExactly(activeUser.getId());
        assertThat(userRepositoryAdapter.count(null, null, null)).isEqualTo(1);
    }

    private User persist(String email, String phone) {
        return entityManager.persistAndFlush(User.register(email, phone, "hash", UserRole.CUSTOMER));
    }

}
