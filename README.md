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
| **step-3** | 직접 만든 세션의 반복 코드·구멍 | Spring Security + HttpSession | 검증된 인증·인가 흐름 | 서버 상태, CSRF 토큰 필요 |
| step-4 | 서버가 세션 저장 | JWT + JwtAuthFilter | 무상태, 헤더라 CSRF 없음 | 털리면 만료까지 유효 |
| step-5 | 긴 토큰 탈취 | access 15분 + refresh 7일 | access 노출 시간 축소 | refresh 저장 위치 문제 |
| step-6 | refresh가 XSS로 읽힘 | refresh를 HttpOnly 쿠키로 | JS가 refresh를 못 읽음 | 쿠키 자동 전송(CSRF) |
| step-7 | CSRF | SameSite + Origin + CORS | 다른 사이트 요청 차단 | 복사된 refresh 폐기 불가 |
| step-8 | refresh 폐기 불가 | refresh 회전 + 서버 저장 | 재사용 탐지, 서버 로그아웃 | 다시 서버 상태(DB 조회) |

`git diff step-0 step-1`처럼 이웃 단계를 비교하면 그 단계에서 바뀐 코드만 보입니다.

---

## step-3: 직접 만든 세션을 HttpSession과 Spring Security로 바꾼다

**이전 문제**: 직접 만든 세션은 만료·CSRF·반복 조회 코드·URL 규칙과 401/403을 전부 손으로 처리해야 함
**해결**: `SessionStore`와 SID 쿠키 처리를 `HttpSession`(JSESSIONID)과 Spring Security 필터 체인으로 바꾼다. 내부 동작 상세는 교안 6-5의 인증 위임 부분을 보세요.

**파일** (`git diff step-2 step-3`)
- `SessionStore`, SID 쿠키 코드 삭제 → `HttpSession` + `HttpSessionSecurityContextRepository`
- `MemberUserDetailsService`, `AuthenticationManagerConfig`, `PasswordAuthenticationService`: 비밀번호 확인을 Spring Security에 위임
- `SecurityConfig`: URL 규칙, 401/403 JSON, 세션 저장소, 로그아웃
- `AuthController`: `/csrf`, `/login`(`changeSessionId()` 후 세션 저장), `/me`
- `PostApiController`: 반복되던 SID 조회 → `@AuthenticationPrincipal String loginId`

**요청 흐름**
```
POST /login → AuthenticationManager → changeSessionId() → SecurityContext를 세션에 저장 → JSESSIONID
PUT /posts/1 (Cookie) → 세션에서 SecurityContext 복원 → CsrfFilter → 인가 → ownPost
```

**확인**: `http/step-3.http` · `StepTest.authorComesFromSessionSoOtherMemberIs403`, `sessionCookieWithoutCsrfTokenIs403`

**남은 문제**: 서버가 세션을 **저장**해야 합니다(서버 여러 대·재시작에 약함). 쿠키라 CSRF 토큰도 따라다닙니다.
