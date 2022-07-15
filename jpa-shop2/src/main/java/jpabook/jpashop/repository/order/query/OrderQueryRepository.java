package jpabook.jpashop.repository.order.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

/**
 * OrderAPIController DTO 조회시 사용
 * - 엔티티 조회시 OrderRepository 사용
 */
@RequiredArgsConstructor
@Repository
public class OrderQueryRepository {

    private final EntityManager em;

    /**
     * V4. 컬렉션은 별도로 조회
     * Query: 루트 1번, 컬렉션 N 번 -> 1 + N 문제
     * 단건 조회에서 많이 사용하는 방식
     */
    public List<OrderQueryDto> findOrderQueryDtos() {
        List<OrderQueryDto> result = findOrders();  // toOne 연관관계는 모두 한번에 조회
        result.forEach(o -> {
            List<OrderItemQueryDto> orderItems = findOrderItems(o.getOrderId()); // 컬렉션 연관관계는 로푸를 돌면서 추가 쿼리 실행
            o.setOrderItems(orderItems);
        });

        return result;
    }

    /**
     * V5. 컬렉션 조회 최적화
     * Query: 루트 1번, 컬렉션 1번
     * - 컬렉션 N 번을 1번의 IN 쿼리로 해결한다.
     * - 1번의 쿼리로 가져온 N 개 데이터를 루프를 돌면서 setXXX() 한다.
     * - 데이터를 한꺼번에 처리할 때 많이 사용하는 방식
     * - DTO 방식이 아닌 엔티티 조회 방식을 사용하면, @BatchSize 로 한번에 조절 가능
     */
    public List<OrderQueryDto> findAllByDto_optimization() {
        List<OrderQueryDto> result = findOrders(); // 쿼리 루트 1번

        List<Long> orderIds = toOrderIds(result);
        Map<Long, List<OrderItemQueryDto>> orderItemMap = findOrderItemMap(orderIds); // 쿼리 컬렉션 1번

        result.forEach(o -> o.setOrderItems(orderItemMap.get(o.getOrderId())));

        return result;
    }

    /**
     * V6. 쿼리 1번
     * - 데이터 중복 제거 로직이 애플리케이션 메모리에서 이루어 져야함.
     * - 페이징 불가능
     *   - 데이터가 많으면 페이징이 필수 -> 사실상 V5 제일 많이 사용
     */
    public List<OrderFlatDto> findAllByDto_flat() {
        return em.createQuery(
                "select new jpabook.jpashop.repository.order.query.OrderFlatDto(o.id, m.name, o.orderDate, o.status, d.address, i.name, oi.orderPrice, oi.count)" +
                        " from Order o" +
                        " join o.member m" +
                        " join o.delivery d" +
                        " join o.orderItems oi" +
                        " join oi.item i", OrderFlatDto.class)
                .getResultList();
    }

    // toMany 연관관계인 orderItems 조회
    private List<OrderItemQueryDto> findOrderItems(Long orderId) {
        return em.createQuery(
                "select new jpabook.jpashop.repository.order.query.OrderItemQueryDto(oi.order.id, i.name, oi.orderPrice, oi.count)" +
                        " from OrderItem oi" +
                        " join oi.item i" +
                        " where oi.order.id = :orderId", OrderItemQueryDto.class)
                .setParameter("orderId", orderId)
                .getResultList();
    }

    // toOne 연관관계는 한번에 조회
    public List<OrderQueryDto> findOrders() {
        return em.createQuery(
                "select new jpabook.jpashop.repository.order.query.OrderQueryDto(o.id, m.name, o.orderDate, o.status, d.address)" +
                        " from Order o" +
                        " join o.member m" +
                        " join o.delivery d", OrderQueryDto.class)
                .getResultList();
    }

    private List<Long> toOrderIds(List<OrderQueryDto> result) {
        return result.stream()
                .map(o -> o.getOrderId())
                .collect(Collectors.toList());
    }

    private Map<Long, List<OrderItemQueryDto>> findOrderItemMap(List<Long> orderIds) {
        List<OrderItemQueryDto> orderItems = em.createQuery(
                        "select new jpabook.jpashop.repository.order.query.OrderItemQueryDto(oi.order.id, i.name, oi.orderPrice, oi.count)" +
                                " from OrderItem oi" +
                                " join oi.item i" +
                                " where oi.order.id in :orderIds", OrderItemQueryDto.class)
                .setParameter("orderIds", orderIds)
                .getResultList();

        Map<Long, List<OrderItemQueryDto>> orderItemMap = orderItems.stream()
                .collect(groupingBy(orderItemQueryDto -> orderItemQueryDto.getOrderId()));

        return orderItemMap;
    }

}