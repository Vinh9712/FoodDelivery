package com.fooddelivery.notification.application;

import com.fooddelivery.notification.api.dto.CustomerNotificationDto;
import com.fooddelivery.notification.domain.model.Notification;
import com.fooddelivery.notification.domain.model.valueobject.Channel;
import com.fooddelivery.notification.domain.model.valueobject.EntityReference;
import com.fooddelivery.notification.domain.model.valueobject.RenderedContent;
import com.fooddelivery.notification.infrastructure.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    @Mock
    private NotificationRepository notificationRepository;

    private CustomerNotificationService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new CustomerNotificationService(
                notificationRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        userId = UUID.randomUUID();
    }

    @Test
    void listMineFiltersUnread() {
        Notification n = sample(userId);
        when(notificationRepository.findByUserIdAndReadFlag(eq(userId), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(n)));

        Page<CustomerNotificationDto> page =
                service.listMine(userId, true, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().title()).isEqualTo("Order update");
        assertThat(page.getContent().getFirst().read()).isFalse();
    }

    @Test
    void markReadUpdatesOwnedNotification() {
        Notification n = sample(userId);
        when(notificationRepository.findByIdAndUserId(n.getId(), userId)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerNotificationDto dto = service.markRead(userId, n.getId());

        assertThat(dto.read()).isTrue();
        assertThat(dto.readAt()).isNotNull();
    }

    @Test
    void markReadRejectsOtherUsersNotification() {
        UUID other = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(other, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(userId, other))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void markAllReadDelegatesToRepository() {
        when(notificationRepository.markAllReadByUserId(userId, NOW)).thenReturn(3);

        assertThat(service.markAllRead(userId)).isEqualTo(3);
        verify(notificationRepository).markAllReadByUserId(userId, NOW);
    }

    private Notification sample(UUID ownerId) {
        return Notification.create(
                "req-key-" + UUID.randomUUID(),
                ownerId,
                "ORDER_NOTIFICATION",
                Channel.IN_APP,
                new RenderedContent("Order update", "Your order is now PAID"),
                new EntityReference("Order", UUID.randomUUID()),
                null);
    }
}
