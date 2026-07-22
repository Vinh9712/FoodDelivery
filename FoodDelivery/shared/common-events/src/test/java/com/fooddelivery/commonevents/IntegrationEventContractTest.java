package com.fooddelivery.commonevents;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonevents.delivery.DeliveryEventPayloads.DeliveryCompleted;
import com.fooddelivery.commonevents.delivery.DeliveryEventPayloads.DeliveryFailed;
import com.fooddelivery.commonevents.delivery.DeliveryEventPayloads.DeliveryInTransit;
import com.fooddelivery.commonevents.delivery.DeliveryEventPayloads.DeliveryPickedUp;
import com.fooddelivery.commonevents.delivery.DeliveryEventPayloads.DriverAssigned;
import com.fooddelivery.commonevents.delivery.DeliveryEventPayloads.DriverSnapshot;
import com.fooddelivery.commonevents.order.OrderEventPayloads.OrderCreated;
import com.fooddelivery.commonevents.order.OrderEventPayloads.OrderCancelled;
import com.fooddelivery.commonevents.order.OrderEventPayloads.OrderRefundStatusChanged;
import com.fooddelivery.commonevents.order.OrderEventPayloads.OrderStatusChanged;
import com.fooddelivery.commonevents.payment.PaymentEventPayloads.PaymentFailed;
import com.fooddelivery.commonevents.payment.PaymentEventPayloads.PaymentRefunded;
import com.fooddelivery.commonevents.payment.PaymentEventPayloads.PaymentSucceeded;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationEventContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void roundTripsDeliveryCompletedWithoutTypeHeaders() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        DeliveryCompleted payload = new DeliveryCompleted(
                orderId, deliveryId, UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-07-22T00:00:00Z"));
        IntegrationEventEnvelope<DeliveryCompleted> event = new IntegrationEventEnvelope<>(
                UUID.randomUUID(), EventContracts.DELIVERY_COMPLETED, 1,
                Instant.parse("2026-07-22T00:00:01Z"), "Delivery", deliveryId, 4L, payload);

        String json = mapper.writeValueAsString(event);
        JavaType type = mapper.getTypeFactory().constructParametricType(
                IntegrationEventEnvelope.class, DeliveryCompleted.class);
        IntegrationEventEnvelope<DeliveryCompleted> restored = mapper.readValue(json, type);

        assertThat(restored).isEqualTo(event);
        assertThat(json).contains("\"eventVersion\":1", "\"aggregateSequence\":4");
    }

    @Test
    void rejectsMissingIdentityAndNonPositiveSequence() {
        assertThatThrownBy(() -> new IntegrationEventEnvelope<>(
                null, "DeliveryCompleted", 1, Instant.now(), "Delivery",
                UUID.randomUUID(), 1L, "payload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
        assertThatThrownBy(() -> new IntegrationEventEnvelope<>(
                UUID.randomUUID(), "DeliveryCompleted", 1, Instant.now(), "Delivery",
                UUID.randomUUID(), 0L, "payload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateSequence");
    }

    @Test
    void rejectsUnsupportedEnvelopeVersion() {
        assertThatThrownBy(() -> new IntegrationEventEnvelope<>(
                UUID.randomUUID(), EventContracts.DELIVERY_COMPLETED, 2, Instant.now(), "Delivery",
                UUID.randomUUID(), 1L, "payload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventVersion");
    }

    @Test
    void exposesExactVersionOneTopics() {
        assertThat(EventContracts.ORDER_EVENTS_V1).isEqualTo("order.events.v1");
        assertThat(EventContracts.DELIVERY_EVENTS_V1).isEqualTo("delivery.events.v1");
        assertThat(EventContracts.PAYMENT_EVENTS_V1).isEqualTo("payment.events.v1");
    }

    @Test
    void keepsMonetaryFieldsAsDecimalStringsAndRoundTripsCurrency() throws Exception {
        PaymentSucceeded payload = new PaymentSucceeded(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "65000.00", "VND", Instant.parse("2026-07-22T00:00:00Z"));

        String json = mapper.writeValueAsString(payload);
        PaymentSucceeded restored = mapper.readValue(json, PaymentSucceeded.class);

        assertThat(restored).isEqualTo(payload);
        assertThat(json).contains("\"amount\":\"65000.00\"", "\"currency\":\"VND\"");
    }

    @Test
    void rejectsBlankAndMissingPayloadFieldsExceptFailedDeliveryDriver() {
        assertThatThrownBy(() -> new OrderCreated(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "", "VND", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalAmount");
        assertThatThrownBy(() -> new PaymentSucceeded(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "65000.00", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");

        DeliveryFailed failed = new DeliveryFailed(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                com.fooddelivery.commonevents.delivery.DeliveryEventPayloads.FailureCode.NO_DRIVER,
                "No driver available", Instant.now());
        assertThat(failed.driverId()).isNull();
    }

    @TestFactory
    Stream<DynamicTest> rejectsARequiredFieldForEveryPayloadRecord() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-22T00:00:00Z");
        List<GuardCase> cases = List.of(
                new GuardCase("OrderCreated.orderId", () -> new OrderCreated(null, id, id, "1.00", "VND", now)),
                new GuardCase("OrderStatusChanged.source", () -> new OrderStatusChanged(id, id, id, "A", "B", null, now)),
                new GuardCase("OrderCancelled.cancellationCode", () -> new OrderCancelled(id, id, id, " ", "reason", "PAID", "NONE", now)),
                new GuardCase("OrderRefundStatusChanged.fromRefundStatus", () -> new OrderRefundStatusChanged(id, id, " ", "PENDING", "reason", now)),
                new GuardCase("DriverSnapshot.fullName", () -> new DriverSnapshot(id, "", "0900000000", "Motorbike", "59A-000.00")),
                new GuardCase("DriverAssigned.driver", () -> new DriverAssigned(id, id, id, null, now)),
                new GuardCase("DeliveryPickedUp.driverId", () -> new DeliveryPickedUp(id, id, id, null, now)),
                new GuardCase("DeliveryInTransit.driverId", () -> new DeliveryInTransit(id, id, id, null, now)),
                new GuardCase("DeliveryCompleted.driverId", () -> new DeliveryCompleted(id, id, id, null, now)),
                new GuardCase("DeliveryFailed.failureCode", () -> new DeliveryFailed(id, id, id, null, null, "reason", now)),
                new GuardCase("PaymentSucceeded.paymentId", () -> new PaymentSucceeded(null, id, id, "1.00", "VND", now)),
                new GuardCase("PaymentFailed.reason", () -> new PaymentFailed(id, id, id, "1.00", "VND", "", now)),
                new GuardCase("PaymentRefunded.refundId", () -> new PaymentRefunded(id, null, id, id, "1.00", "VND", now)));

        return cases.stream().map(testCase -> DynamicTest.dynamicTest(testCase.name(), () ->
                assertThatThrownBy(() -> testCase.construction().run()).isInstanceOf(IllegalArgumentException.class)));
    }

    @TestFactory
    Stream<DynamicTest> rejectsNonCanonicalMoneyForEveryMonetaryPayload() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-22T00:00:00Z");
        List<GuardCase> cases = List.of(
                new GuardCase("OrderCreated.leadingZero", () -> new OrderCreated(id, id, id, "01.00", "VND", now)),
                new GuardCase("PaymentSucceeded.exponent", () -> new PaymentSucceeded(id, id, id, "1e3", "VND", now)),
                new GuardCase("PaymentFailed.plusSign", () -> new PaymentFailed(id, id, id, "+1.00", "VND", "reason", now)),
                new GuardCase("PaymentRefunded.whitespace", () -> new PaymentRefunded(id, id, id, id, " 1.00", "VND", now)));

        return cases.stream().map(testCase -> DynamicTest.dynamicTest(testCase.name(), () ->
                assertThatThrownBy(() -> testCase.construction().run())
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("canonical decimal")));
    }

    private record GuardCase(String name, ThrowingConstruction construction) {}

    @FunctionalInterface
    private interface ThrowingConstruction {
        void run();
    }
}
