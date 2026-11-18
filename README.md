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
| **step-4** | 서버가 세션 저장 | JWT + JwtAuthFilter | 무상태, 헤더라 CSRF 없음 | 털리면 만료까지 유효 |
| step-5 | 긴 토큰 탈취 | access 15분 + refresh 7일 | access 노출 시간 축소 | refresh 저장 위치 문제 |
| step-6 | refresh가 XSS로 읽힘 | refresh를 HttpOnly 쿠키로 | JS가 refresh를 못 읽음 | 쿠키 자동 전송(CSRF) |
| step-7 | CSRF | SameSite + Origin + CORS | 다른 사이트 요청 차단 | 복사된 refresh 폐기 불가 |
| step-8 | refresh 폐기 불가 | refresh 회전 + 서버 저장 | 재사용 탐지, 서버 로그아웃 | 다시 서버 상태(DB 조회) |

`git diff step-0 step-1`처럼 이웃 단계를 비교하면 그 단계에서 바뀐 코드만 보입니다.

---

## step-4: 서버는 저장하지 않고, 클라이언트가 서명된 토큰을 들고 다닌다

**이전 문제**: 서버가 세션 상태를 저장해야 함
**해결**: 서버 비밀키로 서명한 JWT를 주고, 서버는 다음 요청에서 서명만 검증한다(STATELESS).

**파일** (`git diff step-3 step-4`)
- `JwtService`: `createAccess`, `parseAccess`, `bearerValue`
- `JwtAuthFilter`: Bearer 검증 → `SecurityContextHolder`. `@Component`를 붙이지 않음(서블릿 필터로 중복 등록 방지)
- `SecurityConfig`: STATELESS, `addFilterBefore`, `csrf.disable()`(Bearer 헤더는 자동 전송되지 않음)
- `AuthController`: `/login`·`/csrf` → `/token`

**요청 흐름**
```
POST /token → {accessToken}
PUT /posts/1 (Bearer) → JwtAuthFilter: 성공하면 인증 저장, 실패하면 비워 두고 통과 → 인가 401 → ownPost
```
컨트롤러·서비스는 그대로입니다. "누구인지 알아내는 방법"만 필터로 바뀌었습니다.

**확인**: `http/step-4.http` · `StepTest.tokenIdentifiesMemberWithoutSession`, `missingOrBrokenBearerIs401`

**남은 문제**: 토큰이 털리면 만료(1일)까지 그대로 쓰이고, 서버는 저장한 게 없어 막을 수 없습니다(`problem_singleTokenLivesForOneDay`).
