package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.*;
import jpabook.jpashop.repository.order.query.OrderFlatDto;
import jpabook.jpashop.repository.order.query.OrderItemQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.*;

/**
 * xToMany (OneToMany, ManyToMany) 관계 최적화
 * Order -> orderItem
 */
@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    /**
     * V1. 엔티티 직접 노출
     * - 엔티티가 변하면 API 스펙이 변한다.
     * - 트랜잭션 안에서 지연 로딩
     * - 양방향 연관관계 문제
     */
    @GetMapping("/api/v1/orders")
    public List<Order> findOrdersV1() {
        List<Order> all = orderRepository.findAll();
        for (Order order : all) {
            order.getMember().getName();       // Lazy 강제 초기화
            order.getDelivery().getAddress();  // Lazy 강제 초기화
            List<OrderItem> orderItems = order.getOrderItems();
            for (OrderItem orderItem : orderItems) {
                orderItem.getItem().getName(); // Lazy 강제 초기화
            }
        }

        return all;
    }

    /**
     * V2. 엔티티를 조회해서 DTO 변환 (fetch join 사용X)
     * - 트랜잭션 안에서 지연 로딩
     * - DTO 안에 엔티티가 있으면 안 된다.
     *   - OrderDto 안에 있는 OrderItem 도 DTO 변경해햐 한다.
     */
    @GetMapping("/api/v2/orders")
    public Result findOrdersV2() {
        List<Order> orders = orderRepository.findAll();
        List<Object> collect = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(toList());

        return new Result(collect, collect.size());
    }

    /**
     * V3. 엔티티를 조회해서 DTO 변환 및 fetch join 사용
     * 컬렉션 혹은 일대다 페치 조인은 데이터 중복이 발생한다. -> distinct 사용
     *  - 엔티티 주소 마저도 같다. 사실 JPA 는 @Id 식별자 값이 같으면 주소도 같다.
     * 컬렉션 페치 조인은 페이징이 불가능하다.
     *  - 하이버네이트는 메모리에서 페이징한다. 데이터가 많으면 전체 애플리케이션 종료된다.
     */
    @GetMapping("/api/v3/orders")
    public Result findOrdersV3() {
        List<Order> orders = orderRepository.findAllWithItem();;
        List<Object> collect = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(toList());

        return new Result(collect, collect.size());
    }

    @GetMapping("/api/v3.1/orders")
    public Result findOrdersV3_page(@RequestParam(value = "offset", defaultValue = "0")  int offset,
                                    @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<Order> orders = orderRepository.findAllWithMemberDelivery(offset, limit);
        List<Object> collect = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(toList());

        return new Result(collect, collect.size());
    }

    /**
     * V4. JPA 에서 DTO 바로 조회, 컬렉션 N 조회 (1 + N Query 문제)
     * - 페이징 가능
     */
    @GetMapping("/api/v4/orders")
    public Result findOrdersV4() {
        List<OrderQueryDto> result = orderQueryRepository.findOrderQueryDtos();
        return new Result(result, result.size());
    }

    /**
     * V5. JPA에서 DTO로 바로 조회, 컬렉션 1 조회 최적화 버전 (1 + 1 Query)
     * - 페이징 가능
     */
    @GetMapping("/api/v5/orders")
    public Result findOrdersV5() {
        List<OrderQueryDto> result = orderQueryRepository.findAllByDto_optimization();
        return new Result(result, result.size());
    }

    /**
     * V6. JPA 에서 DTO 바로 조회, 플랫 데이터 (1 Query)
     * - 페이징 불가능
     *   - Order 기준으로 불가능 (데이터 중복 발생)
     *   - OrderItems 기준으로는 가능 (데이터 중복이 없으므로)
     * - 중복 데이터를 제거해주는 과정
     */
    @GetMapping("/api/v6/orders")
    public Result findOrdersV6() {
        List<OrderFlatDto> flat = orderQueryRepository.findAllByDto_flat();
        List<OrderQueryDto> result = flat.stream()
                .collect(groupingBy(o -> new OrderQueryDto(o.getOrderId(), o.getName(), o.getOrderDate(), o.getOrderStatus(), o.getAddress()),
                        mapping(o -> new OrderItemQueryDto(o.getOrderId(), o.getItemName(), o.getOrderPrice(), o.getCount()), toList())
                )).entrySet().stream()
                .map(e -> new OrderQueryDto(e.getKey().getOrderId(),
                        e.getKey().getName(), e.getKey().getOrderDate(), e.getKey().getOrderStatus(), e.getKey().getAddress(), e.getValue()))
                .collect(toList());

        return new Result(result, result.size());
    }

    @Data
    @AllArgsConstructor
    static class Result<T> {
        private T data;
        private int count;
    }

    @Data
    static class OrderDto {

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