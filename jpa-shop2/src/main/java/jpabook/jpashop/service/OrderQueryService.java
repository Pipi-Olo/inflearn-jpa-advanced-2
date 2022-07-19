package jpabook.jpashop.service;

import jpabook.jpashop.api.OrderApiController;
import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class OrderQueryService {

    /**
     * OSIV 를 끄면 트랜잭션 안 에서 모든 지연로딩 호출이 이루어 져야 한다.
     * 그거를 위한 쿼리 전용 서비스이다.
     *
     * 기존 OrderService 는 핵심 비지니스 로직만 넣어두고
     * OrderQueryService 는 화면이나 API 에 맞춘 서비스를 제공한다.
     */

    private final OrderRepository orderRepository;

    public List<OrderDto> findOrdersV3() {
        List<Order> orders = orderRepository.findAllWithItem();;
        return orders.stream()
                .map(o -> new OrderDto(o))
                .collect(toList());
    }

    @Data
    public static class OrderDto {

        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address; // Embeddable 는 DTO 변환 없어도 괜찮다. 물론 Address 클래스가 변경되면 API 스펙이 변경된다.
        private List<OrderItemDto> orderItems;

        public OrderDto(Order order) {
            this.orderId = order.getId();
            this.name = order.getMember().getName();
            this.orderDate = order.getOrderDate();
            this.orderStatus = order.getStatus();
            this.address = order.getDelivery().getAddress();

            orderItems = order.getOrderItems().stream()
                    .map(orderItem -> new OrderItemDto(orderItem))
                    .collect(toList());
        }
    }

    @Data
    static class OrderItemDto {

        private String itemName;
        private int orderPrice;
        private int count;

        public OrderItemDto(OrderItem orderItem) {
            this.itemName = orderItem.getItem().getName();
            this.orderPrice = orderItem.getOrderPrice();
            this.count = orderItem.getCount();
        }
    }
}
