package kitchenpos.application;

import kitchenpos.domain.Order;
import kitchenpos.domain.*;
import kitchenpos.helper.*;
import kitchenpos.infra.KitchenridersClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toUnmodifiableList;
import static kitchenpos.helper.MenuHelper.DEFAULT_PRICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

class OrderServiceTest extends BaseServiceTest {

    private static List<Product> createdProducts;
    private static List<MenuProduct> createdMenuProducts;
    private static MenuGroup createdMenuGroup;
    private static Menu displayedMenu1;
    private static Menu displayedMenu2;
    private static Menu hiddenMenu1;
    private static Menu hiddenMenu2;
    private static OrderTable occupiedOrderTable;
    private static OrderTable notOccupiedOrderTable;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderTableRepository orderTableRepository;

    @SpyBean
    private KitchenridersClient kitchenridersClient;


    @BeforeAll
    static void beforeAll(@Autowired ProductService productService,
                          @Autowired MenuGroupService menuGroupService,
                          @Autowired MenuService menuService,
                          @Autowired OrderTableService orderTableService) {

        createdProducts = IntStream.range(1, 5)
                .mapToObj(n -> productService.create(ProductHelper.create(BigDecimal.valueOf(n * 1000L))))
                .collect(toUnmodifiableList());
        createdMenuProducts = IntStream.range(0, createdProducts.size())
                .mapToObj(i -> createMenuProduct(i, i + 1))
                .collect(toUnmodifiableList());
        createdMenuGroup = menuGroupService.create(MenuGroupHelper.create());

        Menu createdMenu1 = menuService.create(MenuHelper.create(DEFAULT_PRICE, createdMenuGroup.getId(), createdMenuProducts));
        displayedMenu1 = menuService.display(createdMenu1.getId());
        Menu createdMenu2 = menuService.create(MenuHelper.create(DEFAULT_PRICE, createdMenuGroup.getId(), createdMenuProducts));
        displayedMenu2 = menuService.display(createdMenu2.getId());

        hiddenMenu1 = menuService.create(MenuHelper.create(DEFAULT_PRICE, createdMenuGroup.getId(), createdMenuProducts));
        hiddenMenu2 = menuService.create(MenuHelper.create(DEFAULT_PRICE, createdMenuGroup.getId(), createdMenuProducts));

        OrderTable createdOrderTable = orderTableService.create(OrderTableHelper.create());
        occupiedOrderTable = orderTableService.sit(createdOrderTable.getId());
        notOccupiedOrderTable = orderTableService.create(OrderTableHelper.create());
    }

    @BeforeEach
    void beforeEach() {
        doAnswer((Answer<Void>) invocation -> null)
                .when(kitchenridersClient)
                .requestDelivery(Mockito.any(), Mockito.any(), Mockito.any());
    }

    private static MenuProduct createMenuProduct(int index, long quantity) {
        MenuProduct menuProduct = new MenuProduct();
        menuProduct.setSeq((long) index);
        menuProduct.setProductId(createdProducts.get(index).getId());
        menuProduct.setProduct(createdProducts.get(index));
        menuProduct.setQuantity(quantity);
        return menuProduct;
    }

    private List<OrderLineItem> createOrderLineItems() {
        OrderLineItem orderLineItem1 = new OrderLineItem();
        orderLineItem1.setMenuId(displayedMenu1.getId());
        orderLineItem1.setQuantity(1);
        orderLineItem1.setPrice(displayedMenu1.getPrice());

        OrderLineItem orderLineItem2 = new OrderLineItem();
        orderLineItem2.setMenuId(displayedMenu2.getId());
        orderLineItem2.setQuantity(2);
        orderLineItem2.setPrice(displayedMenu2.getPrice());

        return List.of(orderLineItem1, orderLineItem2);
    }

    private List<OrderLineItem> createOrderLineItemsWhichPriceIsNotSame() {
        OrderLineItem orderLineItem1 = new OrderLineItem();
        orderLineItem1.setMenuId(displayedMenu1.getId());
        orderLineItem1.setQuantity(1);
        orderLineItem1.setPrice(displayedMenu1.getPrice().add(BigDecimal.ONE));

        OrderLineItem orderLineItem2 = new OrderLineItem();
        orderLineItem2.setMenuId(displayedMenu2.getId());
        orderLineItem2.setQuantity(2);
        orderLineItem2.setPrice(displayedMenu2.getPrice().add(BigDecimal.ONE));

        return List.of(orderLineItem1, orderLineItem2);
    }

    private List<OrderLineItem> createOrderLineItemsThatMenusAreHidden() {
        OrderLineItem orderLineItem1 = new OrderLineItem();
        orderLineItem1.setMenuId(hiddenMenu1.getId());
        orderLineItem1.setQuantity(1);
        orderLineItem1.setPrice(hiddenMenu1.getPrice());

        OrderLineItem orderLineItem2 = new OrderLineItem();
        orderLineItem2.setMenuId(hiddenMenu2.getId());
        orderLineItem2.setQuantity(2);
        orderLineItem2.setPrice(hiddenMenu2.getPrice());

        return List.of(orderLineItem1, orderLineItem2);
    }

    private List<OrderLineItem> createOrderLineItemsWhichQuantityIsNegative() {
        OrderLineItem orderLineItem1 = new OrderLineItem();
        orderLineItem1.setMenuId(displayedMenu1.getId());
        orderLineItem1.setQuantity(-1);
        orderLineItem1.setPrice(displayedMenu1.getPrice());

        OrderLineItem orderLineItem2 = new OrderLineItem();
        orderLineItem2.setMenuId(displayedMenu2.getId());
        orderLineItem2.setQuantity(-2);
        orderLineItem2.setPrice(displayedMenu2.getPrice());

        return List.of(orderLineItem1, orderLineItem2);
    }

    private List<UUID> getOrderedMenuId(List<OrderLineItem> orderLineItems) {
        return orderLineItems
                .parallelStream()
                .map(orderLineItem -> orderLineItem.getMenu().getId())
                .collect(toUnmodifiableList());
    }

    private Order getOrderThatTypeIsDelivery(List<OrderLineItem> orderLineItems, String deliveryAddress) {
        return OrderHelper.createOrderTypeIsDelivery(orderLineItems, deliveryAddress);
    }

    private Order getOrderThatTypeIsEatIn(List<OrderLineItem> orderLineItems, UUID orderTableId) {
        return OrderHelper.createOrderTypeIsEatIn(orderLineItems, orderTableId);
    }

    private Order getOrder(OrderType orderType, List<OrderLineItem> orderLineItems) {
        if (orderType == OrderType.DELIVERY) {
            return OrderHelper.createOrderTypeIsDelivery(orderLineItems, "배달 주소");
        }

        if (orderType == OrderType.EAT_IN) {
            return OrderHelper.createOrderTypeIsEatIn(orderLineItems, occupiedOrderTable.getId());
        }

        if (orderType == OrderType.TAKEOUT) {
            return OrderHelper.createOrderTypeIsTakeOut(orderLineItems);
        }

        return OrderHelper.create(orderType, orderLineItems, null, null);
    }

    @DisplayName("새로운 주문을 등록한다.")
    @Nested
    class CreateOrder {

        @DisplayName("주문 유형은 비어있을 수 없다.")
        @Nested
        class Policy1 {
            @DisplayName("주문 유형이 있는 경우 (성공)")
            @ParameterizedTest
            @EnumSource
            void success1(final OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrder(orderType, orderLineItems);

                // When
                Order createdOrder = orderService.create(order);

                // Then
                assertThat(getOrderedMenuId(createdOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
            }

            @DisplayName("주문 유형이 null 인 경우 (실패)")
            @ParameterizedTest
            @NullSource
            void fail1(OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrder(orderType, orderLineItems);

                // When
                // Then
                assertThatThrownBy(() -> orderService.create(order))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @DisplayName("주문할 메뉴는 1개 이상 있어야 한다.")
        @Nested
        class Policy2 {
            @DisplayName("주문할 메뉴가 1개 이상 있는 경우 (성공)")
            @ParameterizedTest
            @EnumSource
            void success1(final OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrder(orderType, orderLineItems);

                // When
                Order createdOrder = orderService.create(order);

                // Then
                assertThat(getOrderedMenuId(createdOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
                assertThat(createdOrder.getOrderLineItems().size()).isGreaterThan(0);
            }

            @DisplayName("주문할 메뉴가 null 인 경우 (실패)")
            @ParameterizedTest
            @EnumSource
            void fail1(final OrderType orderType) {
                // Given
                Order order = getOrder(orderType, null);

                // When
                // Then
                assertThatThrownBy(() -> orderService.create(order))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @DisplayName("주문할 메뉴가 없는 경우 (실패)")
            @ParameterizedTest
            @EnumSource
            void fail2(final OrderType orderType) {
                // Given
                Order order = getOrder(orderType, List.of());

                // When
                // Then
                assertThatThrownBy(() -> orderService.create(order))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @DisplayName("주문 유형이 매장 식사가 아닌 경우, 주문할 메뉴의 수량은 0개 이상이어야 한다.")
        @Nested
        class Policy3 {
            @DisplayName("주문 유형이 매장 식사일 때, 메뉴의 수량이 음수인 경우(성공)")
            @Test
            void success1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItemsWhichQuantityIsNegative();
                Order order = getOrder(OrderType.EAT_IN, orderLineItems);

                // When
                Order createdOrder = orderService.create(order);

                // Then
                assertThat(getOrderedMenuId(createdOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
            }

            @DisplayName("주문 유형이 배달일 때, 메뉴의 수량이 음수인 경우(실패)")
            @Test
            void fail1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItemsWhichQuantityIsNegative();
                Order order = getOrder(OrderType.DELIVERY, orderLineItems);

                // When
                // Then
                assertThatThrownBy(() -> orderService.create(order))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @DisplayName("주문 유형이 포장일 때, 메뉴의 수량이 음수인 경우(실패)")
            @Test
            void fail2() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItemsWhichQuantityIsNegative();
                Order order = getOrder(OrderType.TAKEOUT, orderLineItems);

                // When
                // Then
                assertThatThrownBy(() -> orderService.create(order))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @DisplayName("주문할 메뉴가 노출된 상태이여야 한다.")
        @Nested
        class Policy4 {
            @DisplayName("주문할 메뉴가 노출된 상태인 경우 (성공)")
            @ParameterizedTest
            @EnumSource
            void success1(final OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrder(orderType, orderLineItems);

                // When
                Order createdOrder = orderService.create(order);

                // Then
                assertThat(getOrderedMenuId(createdOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
            }

            @DisplayName("주문할 메뉴가 숨김 상태인 경우 (실패)")
            @ParameterizedTest
            @EnumSource
            void fail1(final OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItemsThatMenusAreHidden();
                Order order = getOrder(orderType, orderLineItems);

                // When
                // Then
                assertThatThrownBy(() -> orderService.create(order))
                        .isInstanceOf(IllegalStateException.class);
            }
        }

        @DisplayName("주문할 메뉴의 가격은 메뉴에 등록된 가격과 같아야 한다.")
        @Nested
        class Policy5 {
            @DisplayName("주문할 메뉴의 가격과 메뉴에 등록된 가격이 같은 경우 (성공)")
            @ParameterizedTest
            @EnumSource
            void success1(final OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrder(orderType, orderLineItems);

                // When
                Order createdOrder = orderService.create(order);

                // Then
                assertThat(getOrderedMenuId(createdOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
            }

            @DisplayName("주문할 메뉴의 가격과 메뉴에 등록된 가격이 같은 경우 (실패)")
            @ParameterizedTest
            @EnumSource
            void fail1(final OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItemsWhichPriceIsNotSame();
                Order order = getOrder(orderType, orderLineItems);

                // When
                // Then
                assertThatThrownBy(() -> orderService.create(order))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @DisplayName("주문 유형이 배달인 경우, 배달 주소가 있어야 한다.")
        @Nested
        class Policy6 {
            @DisplayName("주문 유형이 배달일 때, 배달 주소가 있는 경우(성공)")
            @ParameterizedTest
            @ValueSource(strings = {" ", "1", "a", "ㄱ", "서울시 용산구", "USA"})
            void success1(final String deliveryAddress) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsDelivery(orderLineItems, deliveryAddress);

                // When
                Order createdOrder = orderService.create(order);

                // Then
                assertThat(getOrderedMenuId(createdOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
            }

            @DisplayName("주문 유형이 배달일 때, 배달 주소가 null 인 경우 (실패)")
            @ParameterizedTest
            @NullSource
            void fail1(final String deliveryAddress) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsDelivery(orderLineItems, deliveryAddress);

                // When
                // Then
                assertThatThrownBy(() -> orderService.create(order))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @DisplayName("주문 유형이 배달일 때, 배달 주소가 비어있는 경우 (실패)")
            @ParameterizedTest
            @ValueSource(strings = {""})
            void fail2(final String deliveryAddress) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsDelivery(orderLineItems, deliveryAddress);

                // When
                // Then
                assertThatThrownBy(() -> orderService.create(order))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @DisplayName("주문 유형이 매장 식사인 경우, 주문 테이블에 입장해 있어야 한다.")
        @Nested
        class Policy7 {
            @DisplayName("주문 유형이 매장 식사일 때, 주문 테이블에 입장해 있는 경우 (성공)")
            @Test
            void success1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsEatIn(orderLineItems, occupiedOrderTable.getId());

                // When
                Order createdOrder = orderService.create(order);

                // Then
                assertThat(getOrderedMenuId(createdOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
            }

            @DisplayName("주문 유형이 매장 식사일 때, 주문 테이블이 존재하지 않는 경우 (실패)")
            @Test
            void fail1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsEatIn(orderLineItems, UUID.randomUUID());

                // When
                // Then
                assertThatThrownBy(() -> orderService.create(order))
                        .isInstanceOf(NoSuchElementException.class);
            }

            @DisplayName("주문 유형이 매장 식사일 때, 주문 테이블에 입장해 있지 않은 경우 (실패)")
            @Test
            void fail2() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsEatIn(orderLineItems, notOccupiedOrderTable.getId());

                // When
                // Then
                assertThatThrownBy(() -> orderService.create(order))
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @DisplayName("들어온 주문을 승낙한다.")
    @Nested
    class AcceptOrder {

        @DisplayName("주문 상태가 대기 상태이여야 한다.")
        @Nested
        class Policy1 {
            @DisplayName("주문 상태가 대기 상태인 경우 (성공)")
            @ParameterizedTest
            @EnumSource
            void success1(final OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrder(orderType, orderLineItems);
                Order createdOrder = orderService.create(order);

                // When
                Order acceptedOrder = orderService.accept(createdOrder.getId());

                // Then
                assertThat(getOrderedMenuId(acceptedOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
                assertThat(acceptedOrder.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
            }

            @DisplayName("주문 상태가 대기 상태가 아닌 경우 (실패)")
            @ParameterizedTest
            @EnumSource
            void fail1(final OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrder(orderType, orderLineItems);
                Order createdOrder = orderService.create(order);
                Order acceptedOrder = orderService.accept(createdOrder.getId());

                // When
                // Then
                assertThatThrownBy(() -> orderService.accept(acceptedOrder.getId()))
                        .isInstanceOf(IllegalStateException.class);
            }
        }

        @DisplayName("주문 유형이 배달이면, 배달 업체에 배달 요청을 한다.")
        @Nested
        class Policy2 {
            @DisplayName("배달 업체에 배달 요청을 성공한 경우 (성공)")
            @Test
            void success1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsDelivery(orderLineItems, "배달 주소");
                Order createdOrder = orderService.create(order);

                // When
                Order acceptedOrder = orderService.accept(createdOrder.getId());

                // Then
                assertThat(getOrderedMenuId(acceptedOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
                assertThat(acceptedOrder.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
            }

            @DisplayName("배달 업체에 배달 요청을 실패한 경우 (실패)")
            @Test
            void fail1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsDelivery(orderLineItems, "배달 주소");
                Order createdOrder = orderService.create(order);

                doThrow(RuntimeException.class)
                        .when(kitchenridersClient)
                        .requestDelivery(Mockito.any(), Mockito.any(), Mockito.any());

                // When
                // Then
                assertThatThrownBy(() -> orderService.accept(createdOrder.getId()))
                        .isInstanceOf(RuntimeException.class);
            }
        }
    }

    @DisplayName("승낙한 주문을 제공한다.")
    @Nested
    class ServeOrder {

        @DisplayName("주문 상태가 승낙 상태이여야 한다.")
        @Nested
        class Policy1 {
            @DisplayName("주문 상태가 승낙 상태인 경우 (성공)")
            @ParameterizedTest
            @EnumSource
            void success1(final OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrder(orderType, orderLineItems);
                Order createdOrder = orderService.create(order);
                Order acceptedOrder = orderService.accept(createdOrder.getId());

                // When
                Order servedOrder = orderService.serve(acceptedOrder.getId());

                // Then
                assertThat(getOrderedMenuId(servedOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
                assertThat(servedOrder.getStatus()).isEqualTo(OrderStatus.SERVED);
            }

            @DisplayName("주문 상태가 승낙 상태가 아닌 경우 (실패)")
            @ParameterizedTest
            @EnumSource
            void fail1(final OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrder(orderType, orderLineItems);
                Order createdOrder = orderService.create(order);

                // When
                // Then
                assertThatThrownBy(() -> orderService.serve(createdOrder.getId()))
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @DisplayName("주문에 대한 배달을 시작한다.")
    @Nested
    class MakeOrderDelivering {

        @DisplayName("주문 유형이 배달이여야 한다.")
        @Nested
        class Policy1 {
            @DisplayName("주문 유형이 배달인 경우 (성공)")
            @Test
            void success1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsDelivery(orderLineItems, "배달 주소");
                Order createdOrder = orderService.create(order);
                Order acceptedOrder = orderService.accept(createdOrder.getId());
                Order servedOrder = orderService.serve(acceptedOrder.getId());

                // When
                Order deliveringOrder = orderService.startDelivery(servedOrder.getId());

                // Then
                assertThat(getOrderedMenuId(deliveringOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
                assertThat(deliveringOrder.getStatus()).isEqualTo(OrderStatus.DELIVERING);
            }

            @DisplayName("주문 유형이 배달인 아닌 경우 (실패)")
            @ParameterizedTest
            @EnumSource(value = OrderType.class, names = {"EAT_IN", "TAKEOUT"})
            void fail1(final OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrder(orderType, orderLineItems);
                Order createdOrder = orderService.create(order);
                Order acceptedOrder = orderService.accept(createdOrder.getId());
                Order servedOrder = orderService.serve(acceptedOrder.getId());

                // When
                // Then
                assertThatThrownBy(() -> orderService.startDelivery(servedOrder.getId()))
                        .isInstanceOf(IllegalStateException.class);
            }
        }

        @DisplayName("주문 상태가 제공 상태이여야 한다.")
        @Nested
        class Policy2 {
            @DisplayName("주문 상태가 제공 상태인 경우 (성공)")
            @Test
            void success1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsDelivery(orderLineItems, "배달 주소");
                Order createdOrder = orderService.create(order);
                Order acceptedOrder = orderService.accept(createdOrder.getId());
                Order servedOrder = orderService.serve(acceptedOrder.getId());

                // When
                Order deliveringOrder = orderService.startDelivery(servedOrder.getId());

                // Then
                assertThat(getOrderedMenuId(deliveringOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
                assertThat(deliveringOrder.getStatus()).isEqualTo(OrderStatus.DELIVERING);
            }

            @DisplayName("주문 상태가 제공 상태가 아닌 경우 (실패)")
            @Test
            void fail1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsDelivery(orderLineItems, "배달 주소");
                Order createdOrder = orderService.create(order);
                Order acceptedOrder = orderService.accept(createdOrder.getId());

                // When
                // Then
                assertThatThrownBy(() -> orderService.startDelivery(acceptedOrder.getId()))
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @DisplayName("주문에 대한 배달을 완료한다.")
    @Nested
    class MakeOrderDelivered {

        @DisplayName("주문 상태가 배달중이여야 한다.")
        @Nested
        class Policy1 {
            @DisplayName("주문 상태가 배달중 상태인 경우 (성공)")
            @Test
            void success1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsDelivery(orderLineItems, "배달 주소");
                Order createdOrder = orderService.create(order);
                Order acceptedOrder = orderService.accept(createdOrder.getId());
                Order servedOrder = orderService.serve(acceptedOrder.getId());
                Order deliveringOrder = orderService.startDelivery(servedOrder.getId());

                // When
                Order deliveredOrder = orderService.completeDelivery(deliveringOrder.getId());

                // Then
                assertThat(getOrderedMenuId(deliveredOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
                assertThat(deliveredOrder.getStatus()).isEqualTo(OrderStatus.DELIVERED);
            }

            @DisplayName("주문 상태가 배달중 상태가 아닌 경우 (실패)")
            @Test
            void fail1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsDelivery(orderLineItems, "배달 주소");
                Order createdOrder = orderService.create(order);
                Order acceptedOrder = orderService.accept(createdOrder.getId());

                // When
                // Then
                assertThatThrownBy(() -> orderService.completeDelivery(acceptedOrder.getId()))
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @DisplayName("주문을 완료한다.")
    @Nested
    class CompleteOrder {

        @DisplayName("주문 유형이 배달이면, 주문 상태가 배달 완료이여야 한다.")
        @Nested
        class Policy1 {
            @DisplayName("주문 상태가 배달 완료 상태인 경우 (성공)")
            @Test
            void success1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsDelivery(orderLineItems, "배달 주소");
                Order createdOrder = orderService.create(order);
                Order acceptedOrder = orderService.accept(createdOrder.getId());
                Order servedOrder = orderService.serve(acceptedOrder.getId());
                Order deliveringOrder = orderService.startDelivery(servedOrder.getId());
                Order deliveredOrder = orderService.completeDelivery(deliveringOrder.getId());

                // When
                Order completedOrder = orderService.complete(deliveredOrder.getId());

                // Then
                assertThat(getOrderedMenuId(completedOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
                assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            }

            @DisplayName("주문 상태가 배달 완료 상태가 아닌 경우 (실패)")
            @Test
            void fail1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsDelivery(orderLineItems, "배달 주소");
                Order createdOrder = orderService.create(order);
                Order acceptedOrder = orderService.accept(createdOrder.getId());
                Order servedOrder = orderService.serve(acceptedOrder.getId());
                Order deliveringOrder = orderService.startDelivery(servedOrder.getId());

                // When
                // Then
                assertThatThrownBy(() -> orderService.complete(deliveringOrder.getId()))
                        .isInstanceOf(IllegalStateException.class);
            }
        }

        @DisplayName("주문 유형이 포장 또는 매장 식사이면, 주문 상태가 제공이여야 한다.")
        @Nested
        class Policy2 {
            @DisplayName("주문 상태가 제공 상태인 경우 (성공)")
            @ParameterizedTest
            @EnumSource(value = OrderType.class, names = {"EAT_IN", "TAKEOUT"})
            void success1(final OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrder(orderType, orderLineItems);
                Order createdOrder = orderService.create(order);
                Order acceptedOrder = orderService.accept(createdOrder.getId());
                Order servedOrder = orderService.serve(acceptedOrder.getId());

                // When
                Order completedOrder = orderService.complete(servedOrder.getId());

                // Then
                assertThat(getOrderedMenuId(completedOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
                assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            }

            @DisplayName("주문 상태가 제공 상태가 아닌 경우 (실패)")
            @ParameterizedTest
            @EnumSource(value = OrderType.class, names = {"EAT_IN", "TAKEOUT"})
            void fail1(final OrderType orderType) {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrder(orderType, orderLineItems);
                Order createdOrder = orderService.create(order);
                Order acceptedOrder = orderService.accept(createdOrder.getId());

                // When
                // Then
                assertThatThrownBy(() -> orderService.complete(acceptedOrder.getId()))
                        .isInstanceOf(IllegalStateException.class);
            }
        }

        @DisplayName("주문 유형이 매장 식사이면, 주문 테이블을 퇴장처리한다.")
        @Nested
        class Policy3 {
            @DisplayName("주문 테이블 퇴장에 성공한 경우 (성공)")
            @Test
            void success1() {
                // Given
                List<OrderLineItem> orderLineItems = createOrderLineItems();
                Order order = getOrderThatTypeIsEatIn(orderLineItems, occupiedOrderTable.getId());
                Order createdOrder = orderService.create(order);
                Order acceptedOrder = orderService.accept(createdOrder.getId());
                Order servedOrder = orderService.serve(acceptedOrder.getId());

                // When
                Order completedOrder = orderService.complete(servedOrder.getId());
                OrderTable orderTable = orderTableRepository.findById(occupiedOrderTable.getId())
                        .orElseThrow(NoSuchElementException::new);

                // Then
                assertThat(getOrderedMenuId(completedOrder.getOrderLineItems())).containsAll(orderLineItems.parallelStream().map(OrderLineItem::getMenuId).collect(toUnmodifiableList()));
                assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
                assertThat(orderTable.getNumberOfGuests()).isZero();
                assertThat(orderTable.isOccupied()).isFalse();
            }
        }
    }

    @DisplayName("모든 주문을 가져온다.")
    @Nested
    class FindAllOrders {

        private List<Order> beforeCreatedOrders;

        @BeforeEach
        void beforeEach() {
            beforeCreatedOrders = IntStream.range(0, 11)
                    .mapToObj(n -> orderService.create(getOrder(OrderType.DELIVERY, createOrderLineItems())))
                    .collect(toUnmodifiableList());
        }

        @DisplayName("모든 주문을 가져온다 (성공)")
        @Test
        void success1() {
            // When
            List<Order> allOrders = orderService.findAll();

            // Then
            assertThat(allOrders.size()).isEqualTo(beforeCreatedOrders.size());
        }
    }

}