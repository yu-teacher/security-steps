# security-steps

검증이 하나도 없는 글 API에서 시작해, **문제를 하나씩 해결하며** Spring Security + JWT + HttpOnly 쿠키 + refresh 회전 구조까지 가는 학습 프로젝트입니다.

- 브랜치 하나 = 한 단계 (`step-0` ~ `step-8`)
- 단계마다 **이전 단계가 남긴 문제**를 해결하고, 해결하면서 생긴 **새 문제**를 다음 단계로 넘깁니다.
- `main`은 최종 상태(`step-8`)에 이 사용법 문서를 더한 브랜치입니다.

## 로드맵

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
| step-8 | refresh 폐기 불가 | refresh 회전 + 서버 저장 | 재사용 탐지, 서버 로그아웃 | 다시 서버 상태(DB 조회) |

## 준비

- JDK 21, Git (DB 설치는 필요 없습니다. H2 메모리 DB를 씁니다.)
- IntelliJ IDEA 권장 (`.http` 요청 파일을 바로 실행할 수 있습니다)

```bash
git clone https://github.com/yu-teacher/security-steps.git
cd security-steps
git checkout step-0
```

## 한 단계를 공부하는 순서

1. **브랜치 이동**: `git checkout step-N`
2. **README 읽기**: 맨 위 로드맵과 이번 단계 섹션(이전 문제 → 해결 → 남은 문제)
3. **바뀐 코드 보기**: `git diff step-(N-1) step-N -- src/main`
   또는 [`docs/security-steps.pdf`](docs/security-steps.pdf)의 해당 장. 터미널 diff가 낯설면 PDF가 더 읽기 쉽습니다.
4. **직접 실행**: `./gradlew bootRun` 후 `http/step-N.http`의 요청을 위에서부터 보내 봅니다.
5. **테스트로 확인**: `./gradlew test`. 테스트 이름이 그 단계의 규칙입니다. `problem_`으로 시작하는 테스트는 **아직 해결되지 않은 문제**를 일부러 보여줍니다.
6. **남은 문제 확인** 후 다음 단계로

## 자주 쓰는 명령

```bash
git checkout step-3
```

```bash
git diff step-2 step-3 -- src/main
```

```bash
git diff --stat step-2 step-3
```

```bash
./gradlew bootRun
```

```bash
./gradlew test
```

- IntelliJ: 오른쪽 아래 Git 브랜치 위젯에서 브랜치를 바꾸고, 브랜치를 우클릭 → **Compare with Current**로 두 단계를 나란히 비교할 수 있습니다.
- 계정: step-1부터 `kdt01` / `kdt1234`, `kdt02` / `kdt1234` (서버를 켤 때마다 다시 만들어집니다)

## 주의

- 코드를 고쳐 보며 공부했다면 다른 단계로 가기 전에 `git stash` 또는 `git restore .`로 정리하세요. 고친 채로 브랜치를 바꾸면 변경이 따라오거나 이동이 막힙니다.
- H2 메모리 DB라 서버를 끄면 글과 토큰 데이터가 모두 사라집니다.
- `application.yml`의 JWT 비밀키는 **학습용 기본값**입니다. 실제 서비스에서는 환경 변수 `JWT_SECRET`으로 바꿔야 합니다.
- 쿠키는 `Secure=false`(로컬 http용)입니다. 운영 환경에서는 HTTPS와 `Secure=true`가 필요합니다.

## 문서

- [`docs/security-steps.pdf`](docs/security-steps.pdf): 모든 단계의 변경 코드를 색으로 표시한 비교 학습 자료 (모든 브랜치에 같은 파일)
- 다시 만들기: `python3 docs/build_pdf.py` (Chrome 필요)
- 최종 단계의 결론·남은 과제·다음 과정 예고는 `step-8` 브랜치 README 끝에 있습니다.
