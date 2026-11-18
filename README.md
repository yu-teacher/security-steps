# security-steps

검증이 전혀 없는 글 API에서 시작해 문제를 하나씩 해결하며 최종 인증 구조까지 가는 연습 프로젝트입니다. 브랜치 하나가 한 단계입니다.

실행 `./gradlew bootRun` (H2 메모리 DB, step-1부터 계정 kdt01·kdt02 / kdt1234) · 테스트 `./gradlew test` · 요청 파일 `http/`

**비교 학습 PDF**: `docs/security-steps.pdf` (모든 단계의 변경을 한 파일로. 다시 만들기: `python3 docs/build_pdf.py`)

**스터디 발표 자료**: `docs/security-steps-study.pptx` (발표자 노트 포함)

| 단계 | 문제 | 해결 | 얻은 것 | 잃은 것 |
|---|---|---|---|---|
| step-0 | — | 검증 없는 글 API | 가장 단순함 | 누가 요청했는지 모름 |
| step-1 | 누가 요청했는지 모름 | 회원 + 비밀번호 해시 로그인 | 로그인 확인 | 작성자를 클라이언트가 주장 |
| step-2 | 서버가 로그인을 기억하지 않음 | 메모리 Map + SID 쿠키 | 서버가 작성자 결정 | 만료·CSRF·인가를 직접 처리 |
| step-3 | 직접 만든 세션의 반복 코드·구멍 | Spring Security + HttpSession | 검증된 인증·인가 흐름 | 서버 상태, CSRF 토큰 필요 |
| step-4 | 서버가 세션 저장 | JWT + JwtAuthFilter | 무상태, 헤더라 CSRF 없음 | 털리면 만료까지 유효 |
| step-5 | 긴 토큰 탈취 | access 15분 + refresh 7일 | access 노출 시간 축소 | refresh 저장 위치 문제 |
| step-6 | refresh가 XSS로 읽힘 | refresh를 HttpOnly 쿠키로 | JS가 refresh를 못 읽음 | 쿠키 자동 전송(CSRF) |
| step-7 | CSRF | SameSite + Origin + CORS | 다른 사이트 요청 차단 | 복사된 refresh 폐기 불가 |
| **step-8** | refresh 폐기 불가 | refresh 회전 + 서버 저장 | 재사용 탐지, 서버 로그아웃 | 다시 서버 상태(DB 조회) |

`git diff step-0 step-1`처럼 이웃 단계를 비교하면 그 단계에서 바뀐 코드만 보입니다.

---

## step-8: refresh를 서버에 저장하고, 쓸 때마다 바꾼다

**이전 문제**: 복사된 refresh를 서버가 폐기할 수 없음
**해결**: refresh의 `jti`를 DB에 저장한다. 재발급하면 옛 refresh를 폐기하고 새 refresh를 저장한다(회전). 폐기된 refresh가 다시 오면 탈취로 보고 그 회원의 refresh를 전부 폐기한다.

**파일** (`git diff step-7 step-8`)
- `RefreshToken`, `RefreshTokenRepository`: jti, loginId, 만료, 폐기 여부
- `AuthService`: 발급 시 저장, 재발급 시 회전·재사용 탐지, `logout`
- `AuthController.logoutCookie`: 쿠키 삭제 + 서버 폐기

**요청 흐름**
```
POST /token/refresh {R1} → R1 폐기, R2 저장 → 200
POST /token/refresh {R1} → 이미 폐기됨 → kdt01의 refresh 전부 폐기 → 401 (R2도 이제 401)
POST /logout-cookie (Cookie R2) → R2 폐기 + 쿠키 삭제
```
재사용을 탐지했을 때 401을 던져도 폐기 결과는 저장돼야 하므로 `@Transactional(noRollbackFor = TokenFailedException.class)`를 씁니다.
같은 refresh로 동시에 두 번 요청하면 둘 다 "아직 폐기 안 됨"을 볼 수 있으므로, `findByJti`에 행 잠금(`@Lock(PESSIMISTIC_WRITE)`)을 걸어 한 요청만 통과시킵니다.

**확인**: `http/step-8.http` · `StepTest.rotatedRefreshCannotBeReused`, `reuseRevokesAllRefreshOfMember`, `concurrentReuseLetsOnlyOneThrough`, `logoutRevokesRefreshOnServer`

## 결론

세션 쿠키를 피해 토큰으로 갔지만, refresh를 지키려다 다시 쿠키를 쓰게 됐고, refresh를 회전·폐기하려다 다시 서버 상태(DB)가 필요해졌습니다. 그래서 최종 구조는 **매 요청은 무상태 access, 재발급만 서버가 기억하는 refresh 쿠키**인, 세션과 토큰을 섞은 형태입니다. HttpOnly는 refresh를 **읽지 못하게** 할 뿐이고 XSS 자체를 막지는 못합니다.

## 최종 파일 지도

| 파일 | 단계 | 해결한 문제 |
|---|---|---|
| `Post`, `PostService`, `PostApiController` | 0 | 글 API |
| `Member`, `PasswordConfig`, `PostService.ownPost` | 1 | 회원·비밀번호 해시, 작성자만 수정·삭제 (401 → 404 → 403) |
| `SessionStore` | 2 (3에서 삭제) | 서버가 로그인 기억 → `HttpSession`으로 대체 |
| `MemberUserDetailsService`, `AuthenticationManagerConfig`, `PasswordAuthenticationService` | 3 | 비밀번호 확인을 Spring Security에 위임 |
| `SecurityConfig` | 3 → 4 → 5 → 6 → 7 | URL 규칙·401/403 / STATELESS·필터 / 경로 / CORS |
| `JwtService` | 4 → 5 | 저장 없이 신원 확인 / access·refresh 구분 |
| `JwtAuthFilter` | 4 | Bearer → `@AuthenticationPrincipal` |
| `TokenResponse`, `RefreshRequest` | 5 | 비밀번호 없이 재발급 |
| `CookieAuthWriter`, `AccessTokenResponse` | 6 → 7 | refresh를 HttpOnly 쿠키로 / SameSite |
| `CookieOriginConfig`, `SecurityCorsConfig` | 7 | 쿠키 요청 출처 확인 |
| `RefreshToken`, `RefreshTokenRepository`, `AuthService` | 8 | refresh 회전·재사용 탐지·서버 로그아웃 |

## 남은 과제

| 과제 | 지금 상태 | 방향 |
|---|---|---|
| XSS 자체 방어 | HttpOnly는 읽기만 막음 | 출력 이스케이프(`textContent`), CSP |
| 로그아웃 후 access | 최대 15분 유효 | 감수하거나 블랙리스트 |
| 만료된 refresh 행 | DB에 계속 쌓임 | 스케줄러로 정리 |
| 여러 기기 로그인 | 재사용을 탐지하면 전부 폐기 | 기기별 refresh 관리 |
| 로그인 시도 제한 | 없음 | 실패 횟수 제한·지연 |
| 운영 환경 | `Secure=false`, http | `Secure=true`, HTTPS |

## 다음 과정 예고

- 비밀번호를 우리 서버가 아예 받지 않는다면, 누가 신원을 확인할까?
- 카카오가 "이 사람 맞다"고 해 주면, 그 사람은 우리 회원 테이블의 누구일까?
- 그때도 지금 만든 access/refresh 구조를 그대로 쓸 수 있을까?
