# security-steps

검증이 전혀 없는 글 API에서 시작해 문제를 하나씩 해결하며 최종 인증 구조까지 가는 연습 프로젝트입니다. 브랜치 하나가 한 단계입니다.

실행 `./gradlew bootRun` (H2 메모리 DB, step-1부터 계정 kdt01·kdt02 / kdt1234) · 테스트 `./gradlew test` · 요청 파일 `http/`

**비교 학습 PDF**: `docs/security-steps.pdf` (모든 단계의 변경을 한 파일로. 다시 만들기: `python3 docs/build_pdf.py`)

**스터디 발표 자료**: `docs/security-steps-study.pptx` (발표자 노트 포함)

| 단계 | 문제 | 해결 | 얻은 것 | 잃은 것 |
|---|---|---|---|---|
| step-0 | — | 검증 없는 글 API | 가장 단순함 | 누가 요청했는지 모름 |
| **step-1** | 누가 요청했는지 모름 | 회원 + 비밀번호 해시 로그인 | 로그인 확인 | 작성자를 클라이언트가 주장 |
| step-2 | 서버가 로그인을 기억하지 않음 | 메모리 Map + SID 쿠키 | 서버가 작성자 결정 | 만료·CSRF·인가를 직접 처리 |
| step-3 | 직접 만든 세션의 반복 코드·구멍 | Spring Security + HttpSession | 검증된 인증·인가 흐름 | 서버 상태, CSRF 토큰 필요 |
| step-4 | 서버가 세션 저장 | JWT + JwtAuthFilter | 무상태, 헤더라 CSRF 없음 | 털리면 만료까지 유효 |
| step-5 | 긴 토큰 탈취 | access 15분 + refresh 7일 | access 노출 시간 축소 | refresh 저장 위치 문제 |
| step-6 | refresh가 XSS로 읽힘 | refresh를 HttpOnly 쿠키로 | JS가 refresh를 못 읽음 | 쿠키 자동 전송(CSRF) |
| step-7 | CSRF | SameSite + Origin + CORS | 다른 사이트 요청 차단 | 복사된 refresh 폐기 불가 |
| step-8 | refresh 폐기 불가 | refresh 회전 + 서버 저장 | 재사용 탐지, 서버 로그아웃 | 다시 서버 상태(DB 조회) |

`git diff step-0 step-1`처럼 이웃 단계를 비교하면 그 단계에서 바뀐 코드만 보입니다.

---

## step-1: 회원과 비밀번호 로그인을 넣는다

**이전 문제**: 누가 요청했는지 알 수 없음
**해결**: 회원 테이블과 BCrypt 비밀번호 로그인을 넣고, 글은 `author` 문자열 대신 `Member`와 연결한다. 작성자만 수정·삭제하도록 검사한다.

**파일** (`git diff step-0 step-1`)
- `Member`, `MemberRepository`, `PasswordConfig`(BCrypt), `SeedRunner`(kdt01·kdt02)
- `AuthService.login`, `AuthController`(`POST /login`)
- `PostService.ownPost`: 회원 401 → 글 404 → 작성자 403 (이 흐름은 끝까지 유지)

**요청 흐름**
```
POST /api/v1/auth/login        → matches → 200 (끝, 아무것도 저장 안 함)
PUT  /api/v1/posts/1 {loginId} → body의 loginId를 그대로 믿고 작성자 비교
```

**확인**: `http/step-1.http` · `StepTest.problem_anyoneCanClaimAnotherLoginId`

**남은 문제**: 로그인 결과를 서버가 기억하지 않으니, kdt02가 body에 `"loginId":"kdt01"`만 넣으면 남의 글을 고칩니다. 작성자는 **서버가** 로그인 결과로 정해야 합니다.
