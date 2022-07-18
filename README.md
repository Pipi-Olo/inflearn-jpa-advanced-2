> 이 글은 김영한님의 **'스프링 부트와 JPA 실무 완전 정복 로드맵'** 강의를 듣고 정리한 내용입니다.
> 강의 : [실전! 스프링 부트와 JPA 활용2 - API 개발과 성능 최적화](https://www.inflearn.com/course/%EC%8A%A4%ED%94%84%EB%A7%81%EB%B6%80%ED%8A%B8-JPA-API%EA%B0%9C%EB%B0%9C-%EC%84%B1%EB%8A%A5%EC%B5%9C%EC%A0%81%ED%99%94/)

# API 개발 기본
```java
@RestController
@RequiredArgsConstructor
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
}
```
```java
@Data
@AllArgsConstructor
static class Result<T> {
    private T data;
    private int count;
}

@Data
@AllArgsConstructor
static class MemberDto {
    private String name;
}

@Data
static class UpdateMemberRequest {
    private String name;
}

@Data
@AllArgsConstructor
static class UpdateMemberResponse {
    private Long id;
    private String name;
}

@Data
static class CreateMemberRequest {
    @NotEmpty
    private String name;
}

@Data
static class CreateMemberResponse {
    private Long id;

    public CreateMemberResponse(Long id) {
        this.id = id;
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
    * `@JsonIgonre` 등 API 스펙을 위한 로직이 엔티티에 추가된다.
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
> `Service` 계층에서 `Controller` 로 엔티티를 반환하지 않는다.
> 트랜잭션 종료 후, 영속성 컨텍스트와 분리된 엔티티를 `Controller` 계층에서 관리하는 것은 좋지 않다. 자세한 것은 `OSIV` 에서 설명한다.

---

