package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.*;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * xToOne (ManyToOne, OneToOne) 관계 최적화
 * Order -> Member, Delivery
 */
@RequiredArgsConstructor
@RestController
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;
    private final OrderSimpleQueryRepository orderSimpleQueryRepository;

    /**
     * V1. 엔티티 직접 노출
     * - 양방향 관계 문제 발생 -> @JsonIgnore 추가
     * - InvalidDefinitionException 예외 발생 -> Hibernate5Module 모듈 등록, LAZY 초기화 (혹은 FORCE_LAZY_LOADING 처리)
     */
    @GetMapping("/api/v1/simple-orders")
    public List<Order> findOrdersV1() {
        List<Order> all = orderRepository.findAll();
        for (Order order : all) {
            order.getMember().getName();      // Lazy 강제 초기화
            order.getDelivery().getAddress(); // Lazy 강제 초기화
        }

        return all;
    }

    /**
     * V2. 각 API 스펙에 맞게 DTO 변환 및 반환 (fetch join 사용 X)
     * - Result 객체로 감싸서 반환한다. -> API 유연성 증가
     * - 1 + N + N 문제 발생 (N : orderList size, Member, Delivery) -> 모든 연관관계 LAZY 설정 및 V3 페치 조인
     */
    @GetMapping("/api/v2/simple-orders")
    public Result findOrdersV2() {
        List<Order> orders = orderRepository.findAll();
        List<SimpleOrderDto> results = orders.stream()
                .map(SimpleOrderDto::new)
                .collect(toList());

        return new Result(results, results.size());
    }

    /**
     * V3. DTO 변환 및 fetch join 사용
     * - fetch join 쿼리 1번 호출
     */
    @GetMapping("/api/v3/simple-orders")
    public Result findOrdersV3() {
        List<Order> orders = orderRepository.findAllWithMemberDelivery();
        List<SimpleOrderDto> results = orders.stream()
                .map(SimpleOrderDto::new)
                .collect(toList());

        return new Result(results, results.size());
    }

    /**
     * V4. 리포지토리에서 DTO 직접 조회
     * - V3 보다 더 좋다고 말할 수 없다.
     *   - V3 메소드와 다르게 DTO 직접 조회이기 때문에 재사용이 불가능하다.
     *   - V4 는 DTO 직접 조회이기 때문에 엔티티 관리가 아니다.
     *   - V4 DTO 직접 조회는 코드가 깔끔하지 않다.
     *   - V3 메소드는 엔티티 조회이기 때문에 비지니스 로직에서 데이터 변경이 가능하다.
     * - select 절에서 가져오는 컬럼의 수 최적화
     *   - from 절 조인은 동일하다.
     *   - select 절 성능 향상이 크지 않다.
     *   - 물론 select 절 컬럼이 20개 넘어가면 상황이 달라진다. 테스트를 통해 확인해야 한다.
     * - 특정 API 에 의존적이다.
     *   - 리포지토리가 특정 컨트롤러에 의존적이다.
     */
    @GetMapping("/api/v4/simple-orders")
    public Result findOrdersV4() {
        List<OrderSimpleQueryDto> orders = orderRepository.findOrderDtos();
        return new Result(orders, orders.size());
    }

    /**
     * V5. DTO 전용 리포지토리 별도 생성
     * - orderRepository 가 특정 컨트롤러에 의존하는 문제 방지한다.
     * - 대신에 조회 전용 (특정 컨트롤러에 의존적인) 리포지토리를 별도 생성한다.
     */
    @GetMapping("/api/v5/simple-orders")
    public Result findOrdersV5() {
        List<OrderSimpleQueryDto> orders = orderSimpleQueryRepository.findOrderDtos();
        return new Result(orders, orders.size());
    }

    @Data
    @AllArgsConstructor
    static class Result<T> {
        private T data;
        private int count;
    }

    @Data
    static class SimpleOrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
        }
    }
}