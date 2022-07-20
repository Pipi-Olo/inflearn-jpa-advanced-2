> 이 글은 김영한님의 **'스프링 부트와 JPA 실무 완전 정복 로드맵'** 강의를 듣고 정리한 내용입니다.
> 강의 : [실전! 스프링 부트와 JPA 활용2 - API 개발과 성능 최적화](https://www.inflearn.com/course/%EC%8A%A4%ED%94%84%EB%A7%81%EB%B6%80%ED%8A%B8-JPA-API%EA%B0%9C%EB%B0%9C-%EC%84%B1%EB%8A%A5%EC%B5%9C%EC%A0%81%ED%99%94/)

# 도메인 분석
![](https://velog.velcdn.com/images/pipiolo/post/b3e28074-146b-424d-a0e9-60d25791163c/image.png)

---

# API 개발 기본
```java
@RestController
public class MemberApiController {

    private final MemberService memberService;

    @GetMapping("/api/v1/members")
    public List<Member> findMembersV1() {
        return memberService.findMembers();
    }

    @GetMapping("/api/v2/members")
    public Result findMembersV2() {
        List<Member> findMembers = memberService.findMembers();
        List<MemberDto> collect = findMembers.stream()
                .map(m -> new MemberDto(m.getName()))
                .collect(Collectors.toList());

        return new Result(collect, collect.size());
    }

    @PostMapping("/api/v1/members")
    public CreateMemberResponse saveMemberV1(@RequestBody @Valid Member member) {
        Long id = memberService.join(member);
        return new CreateMemberResponse(id);
    }

    @PostMapping("/api/v2/members")
    public CreateMemberResponse saveMemberV2(@RequestBody @Valid CreateMemberRequest request) {
        Member member = new Member();
        member.setName(request.getName());

        Long id = memberService.join(member);
        return new CreateMemberResponse(id);
    }

    @PatchMapping("/api/v2/members/{id}")
    public UpdateMemberResponse updateMemberV2(
    		@PathVariable("id") Long id,
            @RequestBody @Valid UpdateMemberRequest request) {
        memberService.update(id, request.getName());
        Member findMember = memberService.findOne(id);
        return new UpdateMemberResponse(findMember.getId(), findMember.getName());
    }
    
    @Data
    @AllArgsConstructor
    static class Result<T> {
        private T data;
        private int count;
    }
}
```
* `Request` 요청
  * `V1` 👉 엔티티를 `@RequestBody` 에 직접 매핑한다.
    * 엔티티 변경 시, API 스펙이 변경된다.
    * 하나의 엔티티에 각 API 모든 요구사항을 반영하기 어렵다.
      * `@NotEmpty`, `@Max` 등 Validation API 검증 로직이 엔티티에 추가된다.
    * 각 API 스펙에 맞는 `DTO` 를 개발한다.
  * `V2` 👉 `DTO` 를 `@RequestBody` 에 매핑한다.
    * 엔티티와 API 스펙을 분리한다.
      * 엔티티와 API 는 유지보수 라이프 사이클이 다르다.
    * 엔티티가 변해도 API 스펙이 변하지 않는다.
* `Response` 응답
  * `V1` 👉 엔티티를 직접 반환한다.
    * 엔티티의 모든 값이 외부에 노출된다.
    * 엔티티 변경 시, API 스펙이 변경된다.
    * API 스펙을 위한 로직이 엔티티에 추가된다.
    * 양방향 연관관계라면 무한 루프에 빠지지 않게 `@JsonIgonre` 를 추가해야 한다.
    * 컬렉션을 직접 반환하면, 향후 API 스펙 변경이 어렵다.
      ```JSON
      [
        {
          "name" : "kim"
          "age" : "20"
        },
        {
          "name" : "hello"
          "age" : "3"
        }
      ]
      ```
      * 배열(`[]`) 안에 JSON(`{}`) 이 존재하는 형태로 `totalCount` 등 추가적인 필드를 넣을 수 없다.
  * `V2` 👉 각 API 스펙에 맞는 `DTO` 를 반환한다.
    * 엔티티가 변해도 API 스펙이 변하지 않는다.
    * `Result` 클래스를 외부에 감싸서 반환한다.
      * 향후 필요한 필드를 추가할 수 있다.
      * API 스펙의 유연성이 증가한다.
* 엔티티를 외부에 노출하면 안 된다. 각 API 스펙에 맞는 `DTO` 를 반환하자.

> **참고**
> `PATCH` 는 엔티티의 부분 업데이트가 필요할 때 사용한다. 전체 업데이트는 `PUT` 을 사용한다.

> **참고**
> `DTO` 변환 시 연관관계에 있는 엔티티들도 모두 `DTO` 로 변환해야 한다. **`DTO` 클래스 안에 엔티티가 들어있어서는 안 된다.**

> **참고**
> `Service` 계층에서 `Controller` 로 엔티티를 반환하지 않는다.
> 트랜잭션 종료 후, 영속성 컨텍스트와 분리된 엔티티를 `Controller` 계층에서 관리하는 것은 좋지 않다. 자세한 것은 `OSIV` 에서 설명한다.

---

# API 개발 고급
## 지연로딩과 조회 성능 최적화
```java
@RestController
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;
    private final OrderSimpleQueryRepository orderSimpleQueryRepository;

    @GetMapping("/api/v1/simple-orders")
    public List<Order> findOrdersV1() {
        List<Order> all = orderRepository.findAll();
        for (Order order : all) {
            order.getMember().getName();      // Lazy 강제 초기화
            order.getDelivery().getAddress(); // Lazy 강제 초기화
        }

        return all;
    }

    @GetMapping("/api/v2/simple-orders")
    public Result findOrdersV2() {
        List<Order> orders = orderRepository.findAll();
        List<SimpleOrderDto> results = orders.stream()
                .map(SimpleOrderDto::new)
                .collect(toList());

        return new Result(results, results.size());
    }

    @GetMapping("/api/v3/simple-orders")
    public Result findOrdersV3() {
        List<Order> orders = orderRepository.findAllWithMemberDelivery();
        List<SimpleOrderDto> results = orders.stream()
                .map(SimpleOrderDto::new)
                .collect(toList());

        return new Result(results, results.size());
    }

    @GetMapping("/api/v4/simple-orders")
    public Result findOrdersV4() {
        List<OrderSimpleQueryDto> orders = orderRepository.findOrderDtos();
        return new Result(orders, orders.size());
    }

    @GetMapping("/api/v5/simple-orders")
    public Result findOrdersV5() {
        List<OrderSimpleQueryDto> orders = orderSimpleQueryRepository.findOrderDtos();
        return new Result(orders, orders.size());
    }
}
```
```java
@Repository
public class OrderRepository {

    public List<Order> findAllWithMemberDelivery() {
        return em.createQuery(
                "select o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d", Order.class)
                .getResultList();
    }
}

@Repository
public class OrderSimpleQueryRepository {

    public List<OrderSimpleQueryDto> findOrderDtos() {
        return em.createQuery(
                "select new jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto(o.id, m.name, o.orderDate, o.status, d.address)" +
                        " from Order o" +
                        " join o.member m" +
                        " join o.delivery d", OrderSimpleQueryDto.class)
                .getResultList();
    }    
}
```

* 성능 문제는 대부분 조회에서 발생한다.
  * 생성, 수정, 삭제는 일반적으로 작은 단위로 일어난다.
* `xToOne` (`ManyToOne`, `OneToOne`) 연관관계 성능 최적화
* `V1` 👉 엔티티 외부 노출
  * 엔티티를 외부에 노출시키는 것은 안 좋다.
  * `member` 와 `address` 는 지연로딩이므로 실제 엔티티 대신에 프록가 존재한다.
  * 객체를 JSON 변환하는 jackson 라이브러리는 프록시 객체 JSON 변환에 대해 모른다. 👉 예외 발생
    * `order.getMember().getName()` 을 통해 지연로딩 강제 초기화한다.
* `V2` 👉 엔티티 조회 및 DTO 변환
  * 쿼리가 1 + N + N 번 실행된다. N + 1 문제가 발생한다.
    * `order` 조회 1번 (N : order 개수)
    * `order → member` 지연로딩 조회 N 번
    * `order → address` 지연로딩 조회 N 번
* `V3` 👉 DTO 변환 및 페치 조인
  * 페치 조인을 통해 쿼리가 1번만 실행된다. → 성능 최적화
  * 마치 즉시 로딩처럼 연관된 엔티티들을 조인해서 1번에 가져온다.
* `V4` 👉 JPA 에서 DTO 직접 조회
  * `new` 명령어를 통해 JPQL 결과를 DTO 로 반환한다.
  * `select` 절에서 가져오는 컬럼의 수가 줄어든다. → 성능 최적화
  * 리포지토리 재사용이 떨어진다.
  * 특정 API 스펙에 의존적인 리포지토리가 생긴다.
    * 각 API 스펙에 의존적인 DTO 를 통해 엔티티를 분리했다.
    * 리포지토리가 특정 DTO 에 의존적이다. → 특정 API 에 의존적이다. → 특정 컨트롤러에 의존적이다.
* `V5` 👉 DTO 조회 전용 리포지토리
  * 유지보수 라이프 사이클이 다르다.
    * 엔티티를 반환하는 핵심 로직과 특정 API 에 의존적인 로직의 라이프 사이클은 다르다.
    * 엔티티 반환 로직과 DTO 반환 로직을 분리한다.
  * `OrderRepository` 는 엔티티 조회만 한다. → 특정 API 에 의존적이지 않다.
  * `OrderSimpleQueryRepository` DTO 조회 전용 리포지토리를 분리한다. → 특정 API 스펙에 의존적이다.
  
> **쿼리 방식 선택 순서**
> * 엔티티를 조회해서 DTO 변환 및 반환한다. 👉 `V2`
> * 페치 조인을 통해 성능 최적화한다. 👉 `V3`
> * 그래도 성능이 해결 안 되면 DTO 직접 조회한다. 👉 `V5`
> * 네이티브 SQL 혹은 JDBC Template 을 통해 SQL 을 직접 사용한다.

## 컬렉션 조회 최적화
```java
@RestController
public class OrderApiController {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    private final OrderQueryService orderQueryService;

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

    @GetMapping("/api/v2/orders")
    public Result findOrdersV2() {
        List<Order> orders = orderRepository.findAll();
        List<OrderDto> collect = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(toList());

        return new Result(collect, collect.size());
    }

    @GetMapping("/api/v3/orders")
    public Result findOrdersV3() {
        List<Order> orders = orderRepository.findAllWithItem();;
        List<OrderDto> collect = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(toList());

        return new Result(collect, collect.size());
    }

    @GetMapping("/api/v3.1/orders")
    public Result findOrdersV3_page(@RequestParam(value = "offset", defaultValue = "0")  int offset,
                                    @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<Order> orders = orderRepository.findAllWithMemberDelivery(offset, limit);
        List<OrderDto> collect = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(toList());

        return new Result(collect, collect.size());
    }

    @GetMapping("/api/v3.2/orders")
    public Result findOrdersV3_queryService() {
        List<OrderQueryService.OrderDto> ordersV3 = orderQueryService.findOrdersV3();
        return new Result(ordersV3, ordersV3.size());
    }

    @GetMapping("/api/v4/orders")
    public Result findOrdersV4() {
        List<OrderQueryDto> result = orderQueryRepository.findOrderQueryDtos();
        return new Result(result, result.size());
    }

    @GetMapping("/api/v5/orders")
    public Result findOrdersV5() {
        List<OrderQueryDto> result = orderQueryRepository.findAllByDto_optimization();
        return new Result(result, result.size());
    }

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
 }
```
```java
@Repository
public class OrderRepository {

    public List<Order> findAllWithItem() {
        return em.createQuery(
                "select distinct o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d" +
                        " join fetch o.orderItems oi" +
                        " join fetch oi.item i", Order.class)
                .getResultList();
    }
    
    public List<Order> findAllWithMemberDelivery(int offset, int limit) {
        return em.createQuery(
                "select o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d", Order.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }    
}

@Repository
public class OrderQueryRepository {

    public List<OrderQueryDto> findOrderQueryDtos() {
        List<OrderQueryDto> result = findOrders();  // toOne 연관관계는 모두 한번에 조회
        result.forEach(o -> {
            List<OrderItemQueryDto> orderItems = findOrderItems(o.getOrderId()); // 컬렉션 연관관계는 로푸를 돌면서 추가 쿼리 실행
            o.setOrderItems(orderItems);
        });

        return result;
    }

    public List<OrderQueryDto> findAllByDto_optimization() {
        List<OrderQueryDto> result = findOrders(); // 쿼리 루트 1번

        List<Long> orderIds = toOrderIds(result);
        Map<Long, List<OrderItemQueryDto>> orderItemMap = findOrderItemMap(orderIds); // 쿼리 컬렉션 1번

        result.forEach(o -> o.setOrderItems(orderItemMap.get(o.getOrderId())));

        return result;
    }

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
```

* `OneToMany` 연관관계 성능 최적화
  * `ManyToMany` 는 사용하지 않는다.
* `V1` 👉 엔티티 외부 노출
* `V2` 👉 엔티티 조회 및 DTO 변환
* `V3` 👉 페치 조인 최적화
  * 일대다 조인은 데이터 중복이 발생한다. → `distinct` 사용한다.
    * JPQL `distinct` 는 SQL 에 `distinct` 를 추가하고 애플리케이션에서 엔티티 중복을 제거한다.
  * `order` 관점에서 데이터 중복이 발생한다. 
    일대다 연관관계인 `orderItems` 관점에서는 데이터 중복이 아니다.
    * 하나의 주문에 주문 상품이 여러 개인 경우, 여러 데이터의 `orderId` 는 동일하지만 `orderItemsId` 는 다르다.
    * 완전히 같은 값은 아니기에 SQL `distinct` 기능으로는 부족하다.
  * 페이징이 불가능하다.
    * 애플리케이션에서 데이터 중복이 해결된다.
    * 하지만, 페이징은 데이터 중복이 존재하는 데이터베이스에서 동작해야 한다.
* `V3.1` 👉 페이징 한계 돌파
  * `ToOne` 연관관계 엔티티는 모두 페치 조인 한다.
  * 컬렉션 엔티티는 지연로딩으로 조회한다. → `1 + N` 문제
    * 하지만, 페치 조인을 하면 데이터 중복이 발생해 페이징 불가능하다.
  * `hibernate_default_batch_size`, `@BatchSize` 사용한다. → `1 + 1` 성능 최적화
    * 프록시 객체를 size 만큼 IN 쿼리로 1번에 조회한다.
    * 조인을 하지 않고 조회하기 때문에 데이터 중복이 없다.
  * 페이징이 가능하다.
* `V4` 👉 JPA 에서 DTO 직접 조회
  * `ToOne` 연관관계들은 한 번에 조회하고 `ToMany` 연관관계는 각각 처리한다. → `1 + N` 문제  
* `V5` 👉 JPA 에서 DTO 직접 조회 + 컬렉션 성능 최적화
  * `ToOne` 연관관계들은 한 번에 조회하고 얻은 식별자를 통해 `ToMany` 연관관계를 한 번에 조회한다. → `1 + 1` 성능 최적화
    * 컬렉션을 한 번의 IN 쿼리를 통해 해결한다.
    * DTO 방식이기 때문에 `@BatchSize` 적용되지 않는다.
* `V6` 👉 JPA 에서 DTO 직접 조회 + 플랫 데이터 성능 최적화
  * 데이터 중복을 고려하지 않고 조인을 통해 한 번에 가져온다. → 성능 최적화
  * 데이터 중복 제거 로직이 애플리케이션 메모리에서 동작한다.
  * 페이징이 불가능하다.
    
> **하이버네이트 컬렉션 페치 조인 페이징**
> 하이버네이트는 경고 로그와 함께 모든 데이터를 데이터베이스에서 읽어온다. 그리고 애플리케이션 메모리에서 페이징을 시도한다. 메모리 부족으로 전체 애플리케이션이 종료될 수 있다.

> **참고**
> 컬렉션 페치 조인은 1개만 사용할 수 있다. 2개 이상의 컬렉션 페치 조인은 데이터 정합성이 깨진다.

> **`hibernate_default_batch_size`**
> 크기는 100 ~ 1000 사이를 권장한다. SQL IN 쿼리를 사용하는데, 데이터베이스에 따라 IN 쿼리를 1000개로 제한한다. 애플리케이션은 100이든 1000이든 결국 메모리 사용량은 같다. 100개를 10번 쿼리를 보내느냐, 1000개를 1번 쿼리로 보내느냐 차이이다.

> **엔티티 조회 vs DTO 조회**
> **엔티티 조회 방식**은 JPA 성능 최적화로 단순한 코드를 유지하면서 성능을 최적화 할 수 있다. 코드를 거의 수정하지 않고 옵션의 변경을 통해 다양한 성능 최적화가 가능하다.
> **DTO 조회 방식**은 성능 최적화를 위해서는 많은 코드의 변경이 필요하다. 성능 최적화와 코드의 복잡도 사이에서 적절한 선택을 해야한다.

> **쿼리 방식 선택 순서**
> * 엔티티 조회 방식
>   * 페치 조인으로 성능 최적화
>     * 페이징 필요 👉 `V3.1`
>     * 페이징 불필요 👉 `V3`
> * DTO 직접 조회 방식 👉 `V5`
> * NativeSQL 혹은 스프링 JDBC Template

---

# OSIV와 성능 최적화
