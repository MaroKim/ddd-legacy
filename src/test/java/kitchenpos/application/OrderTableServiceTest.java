package kitchenpos.application;

import kitchenpos.domain.OrderRepository;
import kitchenpos.domain.OrderStatus;
import kitchenpos.domain.OrderTable;
import kitchenpos.domain.OrderTableRepository;
import kitchenpos.helper.OrderTableHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toUnmodifiableList;
import static kitchenpos.helper.NameHelper.NAME_OF_255_CHARACTERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class OrderTableServiceTest extends BaseServiceTest {

    @Autowired
    private OrderTableService orderTableService;

    @Autowired
    private OrderTableRepository orderTableRepository;

    @SpyBean
    private OrderRepository orderRepository;


    @DisplayName("새로운 테이블을 등록한다.")
    @Nested
    class CreateOrderTable {

        @DisplayName("상품명은 비어있을 수 없고, 255자를 초과할 수 없다.")
        @Nested
        class Policy1 {
            @DisplayName("상품명이 1자 이상 255자 이하인 경우 (성공)")
            @ParameterizedTest
            @ValueSource(strings = {"한", "a", "1", "상품명", "product name", "상품 A", NAME_OF_255_CHARACTERS})
            void success1(final String name) {
                // Given
                OrderTable orderTable = OrderTableHelper.create(name);

                // When
                OrderTable createdOrderTable = orderTableService.create(orderTable);

                // Then
                assertThat(createdOrderTable.getName()).isEqualTo(name);
            }

            @DisplayName("상품명이 null 인 경우 (실패)")
            @ParameterizedTest
            @NullSource
            void fail1(final String name) {
                // When
                OrderTable orderTable = OrderTableHelper.create(name);

                // Then
                assertThatThrownBy(() -> orderTableService.create(orderTable))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @DisplayName("등록된 테이블에 고객을 입장시킨다.")
    @Nested
    class SitOrderTable {

        private OrderTable beforeCreatedOrderTable;

        @BeforeEach
        void beforeEach() {
            beforeCreatedOrderTable = orderTableService.create(OrderTableHelper.create());
        }

        @DisplayName("등록된 테이블에 고객을 입장 (성공)")
        @Test
        void success1() {
            // When
            OrderTable satOrderTable = orderTableService.sit(beforeCreatedOrderTable.getId());

            // Then
            assertThat(satOrderTable.getId()).isEqualTo(beforeCreatedOrderTable.getId());
            assertThat(satOrderTable.isOccupied()).isEqualTo(true);
        }

        @DisplayName("orderTableId null 로 인한 테이블에 고객을 입장 (실패)")
        @ParameterizedTest
        @NullSource
        void fail1(final UUID orderTableId) {
            // When
            // Then
            assertThatThrownBy(() -> orderTableService.sit(orderTableId))
                    .isInstanceOf(InvalidDataAccessApiUsageException.class);
        }

        @DisplayName("존재하지 않는 테이블에 고객을 입장 (실패)")
        @Test
        void fail2() {
            // Given
            UUID notExistedOrderTableId = UUID.randomUUID();

            // When
            // Then
            assertThatThrownBy(() -> orderTableService.sit(notExistedOrderTableId))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @DisplayName("등록된 테이블에 고객을 퇴장시킨다.")
    @Nested
    class ClearOrderTable {

        private OrderTable beforeSatOrderTable;

        @BeforeEach
        void beforeEach() {
            OrderTable orderTable = orderTableService.create(OrderTableHelper.create());
            orderTableService.sit(orderTable.getId());
            beforeSatOrderTable = orderTableService.changeNumberOfGuests(orderTable.getId(), OrderTableHelper.create(5));
        }

        @DisplayName("해당 테이블의 주문 상태가 완료가 아니면, 고객을 퇴장시킬 수 없다.")
        @Nested
        class Policy1 {
            @DisplayName("해당 테이블의 주문 상태가 완료인 경우 (성공)")
            @Test
            void success1() {
                // Given
                OrderTable orderTable = orderTableRepository.findById(beforeSatOrderTable.getId())
                        .orElseThrow(NoSuchElementException::new);
                when(orderRepository.existsByOrderTableAndStatusNot(orderTable, OrderStatus.COMPLETED))
                        .thenReturn(false);

                // When
                OrderTable cleardOrderTable = orderTableService.clear(orderTable.getId());

                // Then
                assertThat(cleardOrderTable.getNumberOfGuests()).isZero();
                assertThat(cleardOrderTable.isOccupied()).isFalse();
            }

            @DisplayName("주문 테이블이 없는 경우 (실패)")
            @Test
            void fail1() {
                // Given
                final UUID orderTableId = UUID.randomUUID();

                // When
                // Then
                assertThatThrownBy(() -> orderTableService.clear(orderTableId))
                        .isInstanceOf(NoSuchElementException.class);
            }

            @DisplayName("해당 테이블의 주문 상태가 완료가 아닌 경우 (성공)")
            @Test
            void fail2() {
                // Given
                OrderTable orderTable = orderTableRepository.findById(beforeSatOrderTable.getId())
                        .orElseThrow(NoSuchElementException::new);
                when(orderRepository.existsByOrderTableAndStatusNot(orderTable, OrderStatus.COMPLETED))
                        .thenReturn(true);

                // When
                // Then
                assertThatThrownBy(() -> orderTableService.clear(orderTable.getId()))
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @DisplayName("입장된 테이블의 고객 수를 변경한다.")
    @Nested
    class ChangeOrderTableNumberOfGuests {

        private OrderTable beforeSatOrderTable;
        private OrderTable beforeNotSatOrderTable;

        @BeforeEach
        void beforeEach() {
            OrderTable orderTable = orderTableService.create(OrderTableHelper.create());
            beforeSatOrderTable = orderTableService.sit(orderTable.getId());
            beforeNotSatOrderTable = orderTableService.create(OrderTableHelper.create());
        }

        @DisplayName("테이블의 고객 수는 0명 이상이어야 한다.")
        @Nested
        class Policy1 {
            @DisplayName("변경할 테이블의 고객 수가 0명 이상인 경우 (성공)")
            @ParameterizedTest
            @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
            void success1(final int numberOfGuests) {
                // Given
                OrderTable orderTable = OrderTableHelper.create(numberOfGuests);

                // When
                OrderTable changedOrderTable = orderTableService.changeNumberOfGuests(beforeSatOrderTable.getId(), orderTable);

                // Then
                assertThat(changedOrderTable.getId()).isEqualTo(beforeSatOrderTable.getId());
                assertThat(changedOrderTable.getName()).isEqualTo(beforeSatOrderTable.getName());
                assertThat(changedOrderTable.getNumberOfGuests()).isEqualTo(numberOfGuests);
            }

            @DisplayName("변경할 테이블의 고객 수가 0명 미만인 경우 (실패)")
            @ParameterizedTest
            @ValueSource(ints = {-1, -100, Integer.MIN_VALUE})
            void fail1(final int numberOfGuests) {
                // Given
                OrderTable orderTable = OrderTableHelper.create(numberOfGuests);

                // When
                // Then
                assertThatThrownBy(() -> orderTableService.changeNumberOfGuests(beforeSatOrderTable.getId(), orderTable))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @DisplayName("테이블에 고객이 입장해 있는 상태여야 한다.")
        @Nested
        class Policy2 {
            @DisplayName("변경할 테이블에 고객이 입장해 있는 경우 (성공)")
            @ParameterizedTest
            @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
            void success1(final int numberOfGuests) {
                // Given
                OrderTable orderTable = OrderTableHelper.create(numberOfGuests);

                // When
                OrderTable changedOrderTable = orderTableService.changeNumberOfGuests(beforeSatOrderTable.getId(), orderTable);

                // Then
                assertThat(changedOrderTable.getId()).isEqualTo(beforeSatOrderTable.getId());
                assertThat(changedOrderTable.getName()).isEqualTo(beforeSatOrderTable.getName());
                assertThat(changedOrderTable.getNumberOfGuests()).isEqualTo(numberOfGuests);
            }

            @DisplayName("변경할 테이블에 고객이 입장해 있지 않은 경우 (실패)")
            @ParameterizedTest
            @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
            void fail1(final int numberOfGuests) {
                // Given
                OrderTable orderTable = OrderTableHelper.create(numberOfGuests);

                // When
                // Then
                assertThatThrownBy(() -> orderTableService.changeNumberOfGuests(beforeNotSatOrderTable.getId(), orderTable))
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @DisplayName("모든 테이블을 가져온다.")
    @Nested
    class FindAllOrderTables {

        private List<OrderTable> beforeCreatedOrderTables;

        @BeforeEach
        void beforeEach() {
            beforeCreatedOrderTables = IntStream.range(0, 11)
                    .mapToObj(n -> orderTableService.create(OrderTableHelper.create()))
                    .collect(toUnmodifiableList());
        }

        @DisplayName("모든 테이블을 가져온다 (성공)")
        @Test
        void success1() {
            // When
            List<OrderTable> allOrderTables = orderTableService.findAll();

            // Then
            assertThat(allOrderTables.size()).isEqualTo(beforeCreatedOrderTables.size());
        }
    }

}