"""step-0 ~ step-8 브랜치를 비교 학습용 PDF로 만든다.

실행: python3 docs/build_pdf.py   (프로젝트 루트에서, Chrome 필요)
결과: docs/security-steps.pdf
"""
import html
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_HTML = ROOT / "docs" / "security-steps.html"
OUT_PDF = ROOT / "docs" / "security-steps.pdf"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
SRC = "src/main/java/com/kdt/steps/"
FULL_LIMIT = 45
CONTEXT = 3

ROADMAP = [
    ("0", "—", "검증 없는 글 API", "가장 단순함", "누가 요청했는지 모름"),
    ("1", "누가 요청했는지 모름", "회원 + 비밀번호 해시 로그인", "로그인 확인", "작성자를 클라이언트가 주장"),
    ("2", "서버가 로그인을 기억하지 않음", "메모리 Map + SID 쿠키", "서버가 작성자 결정", "만료·CSRF·인가를 직접 처리"),
    ("3", "직접 만든 세션의 반복 코드·구멍", "Spring Security + HttpSession", "검증된 인증·인가 흐름", "서버 상태, CSRF 토큰 필요"),
    ("4", "서버가 세션 저장", "JWT + JwtAuthFilter", "무상태, 헤더라 CSRF 없음", "털리면 만료까지 유효"),
    ("5", "긴 토큰 탈취", "access 15분 + refresh 7일", "access 노출 시간 축소", "refresh 저장 위치 문제"),
    ("6", "refresh가 XSS로 읽힘", "refresh를 HttpOnly 쿠키로", "JS가 refresh를 못 읽음", "쿠키 자동 전송(CSRF)"),
    ("7", "CSRF", "SameSite + Origin + CORS", "다른 사이트 요청 차단", "복사된 refresh 폐기 불가"),
    ("8", "refresh 폐기 불가", "refresh 회전 + 서버 저장", "재사용 탐지, 서버 로그아웃", "다시 서버 상태(DB 조회)"),
]

ROLES = {
    "domain/Post.java": "글 엔티티(제목·본문·작성자).",
    "domain/Member.java": "회원 엔티티. loginId, 비밀번호 해시, 닉네임.",
    "domain/RefreshToken.java": "발급한 refresh 한 건을 기록하는 엔티티.",
    "service/PostService.java": "글 업무 로직. 조회·작성·수정·삭제와 작성자 확인.",
    "service/AuthService.java": "로그인·토큰 발급 같은 인증 업무를 모은 서비스.",
    "controller/PostApiController.java": "/api/v1/posts 요청을 받아 PostService에 넘기는 REST 컨트롤러.",
    "controller/AuthController.java": "/api/v1/auth 아래 로그인·토큰·로그아웃 엔드포인트.",
    "dto/PostWriteRequest.java": "글 작성·수정 요청 body(record).",
    "config/PasswordConfig.java": "비밀번호 해시 도구(PasswordEncoder)를 빈으로 등록.",
    "auth/SessionStore.java": "직접 만든 세션 저장소. SID → loginId Map.",
    "config/SecurityConfig.java": "Spring Security 설정의 중심. 어떤 필터를 쓰고 어떤 URL에 누가 들어올 수 있는지 정한다.",
    "auth/MemberUserDetailsService.java": "Spring Security가 \"이 아이디의 회원 정보\"를 물으면 DB에서 찾아 주는 어댑터.",
    "config/AuthenticationManagerConfig.java": "비밀번호 인증기(AuthenticationManager)를 조립해 빈으로 등록.",
    "auth/PasswordAuthenticationService.java": "로그인 요청을 AuthenticationManager에 넘기고, 실패를 우리 401로 바꾼다.",
    "auth/JwtService.java": "JWT 발급과 검증 담당.",
    "auth/JwtAuthFilter.java": "요청마다 Bearer 토큰을 읽어 \"누가 보냈는지\"를 기록하는 필터.",
    "auth/CookieAuthWriter.java": "refresh 쿠키를 만들고 지우는 도우미.",
    "config/CookieOriginConfig.java": "쿠키 경로에 Origin 검사 인터셉터를 등록한다.",
    "config/SecurityCorsConfig.java": "어느 출처(Origin)의 브라우저 요청을 허용할지 정하는 CORS 설정.",
    "repository/RefreshTokenRepository.java": "RefreshToken 조회.",
    "src/main/resources/application.yml": "애플리케이션 설정 파일.",
}

STEPS = {
    0: dict(
        title="아무 검증도 없는 글 API",
        problem="(출발점)",
        solution="글에는 author 문자열만 있고, 누구나 작성·수정·삭제할 수 있다.",
        terms=[
            ("@RestController", "메서드 반환값을 JSON 응답 body로 바로 쓰는 컨트롤러."),
            ("@Transactional(readOnly = true)", "클래스 기본은 읽기 전용 트랜잭션, 쓰기 메서드에만 @Transactional을 다시 붙인다."),
            ("record DTO", "요청·응답 모양만 정의하는 불변 클래스. 엔티티를 그대로 내보내지 않으려고 쓴다."),
        ],
        flow=["POST   /api/v1/posts {author, title, body} → 201",
              "PUT    /api/v1/posts/1  → 200 (누가 보냈는지 확인 안 함)",
              "DELETE /api/v1/posts/1  → 204"],
        files={
            "domain/Post.java": ("작성자는 클라이언트가 적은 문자열(author)일 뿐이다.", {
                "edit": "제목과 본문을 바꾼다. setter 대신 의미 있는 이름의 메서드로 변경을 한곳에 모은다.",
            }),
            "service/PostService.java": ("글이 있는지만 확인하고 바로 수정·삭제한다.", {
                "list": "모든 글을 id 순으로 조회해 응답 DTO로 바꾼다.",
                "create": "요청의 author·title·body로 글을 만들어 저장한다.",
                "update": "글을 찾아 제목·본문을 바꾼다. 트랜잭션이 끝날 때 변경이 자동 반영된다(변경 감지).",
                "delete": "글을 찾아 삭제한다.",
                "find": "id로 글을 찾고, 없으면 404(NotFoundException).",
            }),
            "controller/PostApiController.java": ("요청을 그대로 서비스에 넘긴다.", {
                "list": "GET /api/v1/posts → 200 목록.",
                "create": "POST /api/v1/posts → @Valid로 body를 검증한 뒤 201.",
                "update": "PUT /api/v1/posts/{id} → 200.",
                "delete": "DELETE /api/v1/posts/{id} → 204.",
            }),
        },
        tests=["problem_anyoneCanEditOthersPost", "problem_anyoneCanDeleteOthersPost"],
        next="서버는 누가 요청했는지 알 수 없다.",
    ),
    1: dict(
        title="회원과 비밀번호 로그인을 넣는다",
        problem="누가 요청했는지 알 수 없다.",
        solution="회원 테이블과 BCrypt 로그인을 넣고, 글을 Member와 연결한 뒤 작성자만 수정·삭제하게 한다.",
        terms=[
            ("해시와 BCrypt", "해시는 되돌릴 수 없는 변환이다. BCrypt는 같은 비밀번호도 매번 다른 해시를 만들기 때문에(salt) matches로만 비교한다."),
            ("401 / 403 / 404", "401: 누구인지 확인 못 함. 403: 누구인지는 알지만 권한 없음. 404: 대상이 없음."),
        ],
        flow=["POST /api/v1/auth/login        → matches → 200 (아무것도 저장 안 함)",
              "PUT  /api/v1/posts/1 {loginId} → body의 loginId를 믿고 작성자 비교"],
        files={
            "domain/Member.java": ("password에는 원문이 아니라 해시만 저장한다.", {}),
            "config/PasswordConfig.java": ("BCrypt 등록.", {
                "passwordEncoder": "BCryptPasswordEncoder를 빈으로 등록한다. encode는 해시 생성, matches는 입력 비밀번호와 저장된 해시 비교.",
            }),
            "service/AuthService.java": ("로그인 확인.", {
                "login": "loginId로 회원을 찾고 matches로 비교한다. 아이디가 없든 비밀번호가 틀리든 같은 401을 던져 어느 쪽이 틀렸는지 알려 주지 않는다.",
            }),
            "service/PostService.java": ("작성자 확인이 생겼다. 이 검사 순서는 끝까지 유지된다.", {
                "create": "caller(loginId)로 찾은 회원을 작성자로 저장한다.",
                "update": "ownPost로 권한을 확인한 뒤 수정한다.",
                "delete": "ownPost로 권한을 확인한 뒤 삭제한다.",
                "caller": "loginId로 회원을 찾는다. 없으면 401.",
                "ownPost": "회원 확인(401) → 글 확인(404) → 작성자 비교(403) 순서로 검사하고, 통과하면 글을 돌려준다.",
            }),
            "domain/Post.java": ("author 문자열 → Member 연관관계.", {}),
            "dto/PostWriteRequest.java": ("author 대신 loginId를 받는다. 이 값을 클라이언트가 정하는 것이 문제다.", {}),
            "controller/PostApiController.java": ("클라이언트가 보낸 loginId를 그대로 서비스에 넘긴다.", {
                "create": "body의 loginId를 작성자로 넘긴다.",
                "update": "body의 loginId로 작성자 비교를 요청한다.",
                "delete": "쿼리 파라미터 loginId로 작성자 비교를 요청한다.",
            }),
        },
        minor="build.gradle(spring-security-crypto), SeedRunner(kdt01·kdt02), AuthController(/login), "
              "MemberRepository, LoginRequest, MemberResponse, PostResponse, "
              "UnauthorizedException·LoginFailedException(401), ForbiddenException(403), ApiExceptionAdvice",
        tests=["loginChecksPassword", "honestOtherMemberIsForbidden", "problem_anyoneCanClaimAnotherLoginId"],
        next="kdt02가 body에 \"loginId\":\"kdt01\"만 넣으면 남의 글을 고친다. 작성자는 서버가 정해야 한다.",
    ),
    2: dict(
        title="세션을 직접 만들어 본다 (메모리 Map + SID 쿠키)",
        problem="서버가 로그인 결과를 기억하지 않아 클라이언트가 아무 loginId나 주장한다.",
        solution="로그인에 성공하면 UUID(SID)를 만들어 서버 Map에 SID → loginId로 저장하고, SID는 쿠키로 준다.",
        terms=[
            ("쿠키와 Set-Cookie", "서버가 Set-Cookie 헤더로 값을 주면, 브라우저는 이후 같은 사이트 요청에 Cookie 헤더로 자동으로 붙여 보낸다."),
            ("HttpOnly", "JavaScript(document.cookie)로 읽을 수 없는 쿠키. XSS로 값을 훔쳐 가지 못하게 한다."),
            ("세션", "서버가 로그인 상태를 저장하고, 클라이언트는 그 저장소의 키(세션 ID)만 들고 다니는 방식."),
        ],
        flow=["POST /login → 비밀번호 확인 → sessions.put(UUID, \"kdt01\") → Set-Cookie: SID",
              "PUT /posts/1 (Cookie: SID) → sessions.get(SID) → 없으면 401 → ownPost",
              "POST /logout → sessions.remove(SID) + 쿠키 삭제"],
        files={
            "auth/SessionStore.java": ("JSESSIONID의 정체. 서버 메모리의 Map이고, SID는 그 키다.", {
                "create": "UUID로 추측할 수 없는 SID를 만들고 Map에 SID → loginId를 저장한다.",
                "loginId": "SID로 Map을 조회해 loginId를 돌려준다. SID가 없거나 Map에 없으면 401.",
                "remove": "Map에서 SID를 지운다(로그아웃).",
            }),
            "controller/AuthController.java": ("로그인하면 SID 쿠키 발급, 로그아웃하면 Map과 쿠키 삭제.", {
                "login": "비밀번호를 확인한 뒤 SessionStore에 저장하고 SID를 쿠키로 내려준다.",
                "logout": "Map에서 SID를 지우고, Max-Age=0 쿠키로 브라우저 쿠키도 지운다.",
                "writeSid": "HttpOnly·Path=/ 쿠키를 Set-Cookie 헤더로 쓴다.",
            }),
            "controller/PostApiController.java": ("모든 메서드가 쿠키에서 SID를 꺼내 Map을 조회한다. 이 반복이 다음 단계의 이유다.", {
                "create": "@CookieValue로 SID를 받아 sessionStore.loginId(sid)로 작성자를 정한다.",
                "update": "create와 같은 방식으로 loginId를 얻어 수정을 요청한다.",
                "delete": "create와 같은 방식으로 loginId를 얻어 삭제를 요청한다.",
            }),
            "dto/PostWriteRequest.java": ("loginId 삭제. 작성자는 이제 서버가 정한다.", {}),
        },
        tests=["authorComesFromServerMapSoOtherMemberIs403", "logoutRemovesSidFromServer"],
        next="Map의 SID는 지우기 전까지 남고(만료), 쿠키라 CSRF에 노출되며, 조회·URL 규칙·401/403을 전부 손으로 만든다.",
    ),
    3: dict(
        title="직접 만든 세션을 Spring Security에 맡긴다",
        problem="직접 만든 세션은 만료·CSRF·반복 조회 코드·URL 규칙과 401/403을 전부 손으로 처리해야 한다.",
        solution="SessionStore를 HttpSession(JSESSIONID)과 Spring Security 필터 체인으로 바꾼다.",
        terms=[
            ("SecurityFilterChain", "요청이 컨트롤러에 닿기 전에 차례로 거치는 보안 필터 묶음. 인증 복원, CSRF 검사, 인가가 여기서 일어난다."),
            ("AuthenticationManager", "\"이 아이디·비밀번호가 맞나?\"를 판단하는 창구. 실제 일은 DaoAuthenticationProvider가 "
                                      "UserDetailsService(회원 찾기)와 PasswordEncoder(비교)로 한다."),
            ("SecurityContext / SecurityContextHolder", "현재 요청의 인증 정보(누구인지)를 담는 상자와, 그 상자를 요청 스레드에 보관하는 곳(ThreadLocal)."),
            ("@AuthenticationPrincipal", "SecurityContext에 든 사용자(여기서는 loginId 문자열)를 컨트롤러 파라미터로 받는다."),
            ("EntryPoint / AccessDeniedHandler", "인증이 없을 때 401, 권한이 없을 때 403 응답을 만드는 곳."),
            ("CSRF 토큰", "다른 사이트는 알 수 없는 값을 요청에 함께 보내게 해서, 쿠키만 자동으로 붙은 위조 요청을 거절한다."),
            ("changeSessionId", "로그인 순간 세션 ID를 바꿔, 로그인 전에 심어 둔 ID를 공격자가 쓰지 못하게 한다(세션 고정 방지)."),
        ],
        flow=["POST /login → AuthenticationManager → changeSessionId() → SecurityContext를 세션에 저장",
              "PUT /posts/1 (Cookie) → 세션에서 SecurityContext 복원 → CsrfFilter → 인가 → ownPost"],
        files={
            "auth/SessionStore.java": ("직접 만든 Map은 지우고 HttpSession(JSESSIONID)에 맡긴다.", {}),
            "config/SecurityConfig.java": ("CSRF 보호는 기본으로 켜져 있다.", {
                "securityContextRepository": "인증 정보(SecurityContext)를 HttpSession에 저장·복원하는 저장소를 빈으로 등록한다. 로그인 컨트롤러도 같은 저장소를 쓴다.",
                "securityFilterChain": "폼 로그인·HTTP Basic 끄기, 로그아웃 URL, URL별 접근 규칙(permitAll·authenticated·denyAll), 인증 없음 401과 권한 없음 403 응답을 정한다.",
                "writeError": "필터 단계 오류를 {code, message} JSON으로 쓴다. 필터에서 난 오류는 @RestControllerAdvice까지 가지 않아서 직접 쓴다.",
            }),
            "auth/MemberUserDetailsService.java": ("회원 찾기 담당.", {
                "loadUserByUsername": "loginId로 회원을 찾아 Spring Security가 쓰는 UserDetails(아이디·해시·권한 ROLE_USER)로 바꾼다. 없으면 UsernameNotFoundException.",
            }),
            "config/AuthenticationManagerConfig.java": ("회원 찾기 + 비밀번호 비교를 하나로 조립한다.", {
                "authenticationManager": "DaoAuthenticationProvider에 UserDetailsService와 PasswordEncoder를 꽂고 ProviderManager로 감싼다.",
            }),
            "auth/PasswordAuthenticationService.java": ("step-1에서 직접 하던 matches 비교를 위임한다.", {
                "authenticateLoginId": "BCrypt 한계(72바이트)를 넘는 비밀번호는 먼저 거절하고, 미인증 토큰을 AuthenticationManager에 넘긴다. 성공하면 loginId를 돌려주고, 실패 예외는 우리 401로 바꾼다.",
            }),
            "controller/AuthController.java": ("SID 쿠키 코드가 사라지고 세션 저장으로 바뀐다.", {
                "csrf": "현재 세션의 CSRF 토큰을 돌려준다. 클라이언트는 POST·PUT·DELETE에 이 값을 X-CSRF-TOKEN 헤더로 보낸다.",
                "login": "비밀번호 확인 → changeSessionId()로 세션 ID 교체 → loginId를 담은 SecurityContext를 세션에 저장.",
                "me": "@AuthenticationPrincipal로 현재 로그인한 loginId를 돌려준다.",
            }),
            "controller/PostApiController.java": ("반복되던 SID 조회가 파라미터 하나로 바뀐다.", {
                "create": "@AuthenticationPrincipal String loginId로 작성자를 받는다. 세션 조회는 필터가 이미 끝냈다.",
                "update": "create와 같은 방식.",
                "delete": "create와 같은 방식.",
            }),
        },
        minor="build.gradle(spring-boot-starter-security), AuthService.login(PasswordAuthenticationService 사용), MeResponse",
        tests=["authorComesFromSessionSoOtherMemberIs403", "sessionCookieWithoutCsrfTokenIs403", "logoutEndsSession"],
        next="서버가 세션을 저장해야 한다(서버 여러 대·재시작에 약함). 쿠키라 CSRF 토큰도 따라다닌다.",
    ),
    4: dict(
        title="서버는 저장하지 않고, 서명된 토큰을 검증한다 (JWT)",
        problem="서버가 세션 상태를 저장해야 한다.",
        solution="서버 비밀키로 서명한 JWT를 주고, 다음 요청에서는 서명만 검증한다(STATELESS).",
        terms=[
            ("JWT", "header.payload.signature 세 부분의 문자열. payload에 sub(사용자)·exp(만료) 같은 클레임을 담고, 서버 비밀키 서명으로 위조를 막는다. "
                    "암호화가 아니라서 내용은 누구나 읽을 수 있다."),
            ("Bearer", "Authorization: Bearer <토큰> 형식. 쿠키와 달리 브라우저가 자동으로 붙이지 않는다."),
            ("STATELESS", "서버가 세션을 만들지도 쓰지도 않는다. 매 요청을 토큰만으로 판단한다."),
            ("OncePerRequestFilter", "한 요청에 정확히 한 번 실행되는 필터의 기본 클래스. doFilterInternal만 구현하면 된다."),
        ],
        flow=["POST /token → {accessToken}",
              "PUT /posts/1 (Bearer) → JwtAuthFilter: 성공하면 인증 저장, 실패하면 비워 두고 통과",
              "→ 인가(없으면 401) → ownPost"],
        files={
            "auth/JwtService.java": ("세션 저장소 대신 서명이 신원을 보증한다.", {
                "createAccess": "sub=loginId, 발급·만료 시각을 넣고 비밀키로 HS256 서명한다.",
                "parseAccess": "서명과 만료를 검증해 클레임을 꺼낸다. 위조·만료면 401(TokenFailedException).",
                "bearerValue": "'Bearer ' 접두어를 떼어 토큰만 꺼낸다. 형식이 틀리면 401.",
            }),
            "auth/JwtAuthFilter.java": ("@Component를 붙이지 않는다. 붙이면 서블릿 필터로도 등록돼 두 번 실행된다.", {
                "doFilterInternal": "Authorization 헤더가 Bearer면 토큰을 검증해 loginId를 SecurityContextHolder에 넣는다. "
                                    "실패해도 막지 않고 비워 둔 채 다음 필터로 넘긴다. 막는 건 뒤의 인가 단계(401)의 일이다.",
            }),
            "config/SecurityConfig.java": ("세션 관련 설정이 빠지고 필터가 들어온다.", {
                "securityFilterChain": "csrf 끄기(Bearer는 자동 전송되지 않음), 세션 STATELESS, 허용 URL을 /token으로 교체, "
                                       "JwtAuthFilter를 UsernamePasswordAuthenticationFilter 앞에 추가.",
            }),
            "controller/AuthController.java": ("/login·/csrf가 /token 하나로 바뀐다.", {
                "issueTokens": "POST /token: 비밀번호 확인 후 access 토큰을 JSON으로 준다.",
            }),
            "service/AuthService.java": ("로그인 결과가 회원 정보에서 토큰으로 바뀐다.", {
                "issueTokens": "PasswordAuthenticationService로 loginId를 확인하고 JwtService로 access를 만든다.",
            }),
        },
        minor="build.gradle(jjwt), application.yml(비밀키, 1일), TokenResponse, TokenFailedException, "
              "PostService(회원 없음 → TokenFailedException), MemberResponse 삭제",
        tests=["tokenIdentifiesMemberWithoutSession", "missingOrBrokenBearerIs401", "otherMemberIs403"],
        next="토큰이 털리면 만료(1일)까지 그대로 쓰이고, 서버는 저장한 게 없어 막을 수 없다.",
    ),
    5: dict(
        title="요청용은 짧게, 재발급용은 길게 (access + refresh)",
        problem="긴 토큰이 털리면 오래 쓰인다.",
        solution="매 요청에 실리는 access는 15분, 새 access를 받는 데만 쓰는 refresh는 7일로 나눈다.",
        terms=[
            ("클레임(claim)", "토큰 payload의 각 항목. 여기서는 sub, jti, tokenType, iat(발급), exp(만료)."),
            ("jti", "토큰마다 붙인 고유 ID(UUID). step-8에서 refresh를 DB에 기록하는 열쇠가 된다."),
            ("access / refresh", "access는 매 API 요청용(짧게), refresh는 새 access를 받는 데만 쓰는 토큰(길게)."),
        ],
        flow=["POST /token → {access, refresh}",
              "API 401 → POST /token/refresh {refresh} → 새 access·refresh → 원래 요청 재시도",
              "refresh를 Bearer로 보내면 → tokenType 불일치 → 401"],
        files={
            "auth/JwtService.java": ("tokenType 클레임으로 두 토큰을 구분한다. 없으면 같은 키로 서명된 refresh를 access처럼 쓸 수 있다.", {
                "createAccess": "compact로 tokenType=access, 15분 토큰을 만든다.",
                "createRefresh": "compact로 tokenType=refresh, 7일 토큰을 만든다.",
                "parseAccess": "tokenType이 access인 토큰만 통과시킨다.",
                "parseRefresh": "tokenType이 refresh인 토큰만 통과시킨다.",
                "compact": "공통 발급 코드: sub, jti(UUID), tokenType, iat, exp를 넣고 서명한다.",
                "parseWithType": "서명·만료를 검증한 뒤 tokenType이 기대값과 다르면 401.",
            }),
            "service/AuthService.java": ("두 토큰을 한 쌍으로 발급한다.", {
                "issueTokens": "비밀번호 확인 후 tokensFor로 한 쌍을 발급한다.",
                "refresh": "refresh를 검증해 loginId를 꺼내고, 비밀번호 없이 새 한 쌍을 발급한다.",
                "tokensFor": "같은 loginId로 access와 refresh를 만들어 TokenResponse로 묶는다.",
            }),
            "controller/AuthController.java": ("재발급 엔드포인트 추가.", {
                "refresh": "POST /token/refresh: body의 refreshToken으로 재발급한다.",
            }),
            "src/main/resources/application.yml": ("access 900초(15분), refresh 604800초(7일).", {}),
        },
        minor="SecurityConfig(/token/refresh 허용), RefreshRequest, TokenResponse(+refreshToken)",
        tests=["accessIsShortAndRefreshIsLong", "refreshCannotBeUsedAsBearer", "refreshIssuesNewPairButAccessCannotRefresh"],
        next="refresh를 JSON으로 받으니 JS가 localStorage 등에 저장해야 하고, XSS가 있으면 그대로 읽힌다.",
    ),
    6: dict(
        title="refresh는 JS가 못 읽는 HttpOnly 쿠키로",
        problem="refresh를 JS 저장소에 두면 XSS로 읽힌다.",
        solution="refresh는 Set-Cookie(HttpOnly, Path=/api/v1/auth)로 주고, 응답 JSON에는 access만 담는다.",
        terms=[
            ("XSS", "공격자 스크립트가 우리 페이지에서 실행되는 것. localStorage 값은 그대로 읽힌다."),
            ("Path / Max-Age", "Path: 이 경로 아래 요청에만 쿠키를 보낸다. Max-Age: 쿠키 수명(초). 0이면 즉시 삭제."),
            ("ResponseCookie", "Spring의 쿠키 빌더. SameSite 같은 속성까지 지정해 Set-Cookie 문자열을 만든다."),
        ],
        flow=["POST /token-cookie → {accessToken} + Set-Cookie: refreshToken; HttpOnly",
              "POST /token/refresh-cookie → 브라우저가 쿠키 자동 첨부 → 새 access + 새 쿠키",
              "POST /logout-cookie → Max-Age=0"],
        files={
            "auth/CookieAuthWriter.java": ("쿠키 속성을 한곳에서 관리한다.", {
                "writeRefresh": "refresh를 7일짜리 쿠키로 응답에 쓴다.",
                "clearRefresh": "같은 이름·경로의 빈 쿠키를 Max-Age=0으로 보내 브라우저에서 지운다.",
                "refreshCookie": "쿠키 속성 조립: HttpOnly, Secure(false, 로컬 http용), Path=/api/v1/auth, Max-Age.",
                "add": "완성한 쿠키를 Set-Cookie 헤더로 추가한다.",
            }),
            "controller/AuthController.java": ("쿠키 방식 엔드포인트 3개. JSON /token은 앱 등 비브라우저용으로 남긴다.", {
                "tokenCookie": "POST /token-cookie: 토큰 쌍을 발급해 refresh는 쿠키로, access만 JSON으로 준다.",
                "refreshCookie": "POST /token/refresh-cookie: 쿠키의 refresh로 재발급하고 새 refresh로 쿠키를 갈아 끼운다. 쿠키가 없으면 401.",
                "logoutCookie": "POST /logout-cookie: refresh 쿠키를 지운다. 서버에는 아직 아무 기록도 없다.",
            }),
            "config/SecurityConfig.java": ("쿠키 경로 허용.", {
                "securityFilterChain": "쿠키 경로 3개를 permitAll에 추가한다.",
            }),
        },
        minor="AccessTokenResponse(access만 담는 응답)",
        tests=["cookieLoginHidesRefreshFromJs", "refreshCookieIssuesNewAccessAndNewCookie",
               "refreshCookieMissingIs401AndLogoutClearsCookie", "problem_cookieIsSentFromAnyOrigin"],
        next="쿠키는 다른 사이트가 보낸 요청에도 자동으로 붙는다(CSRF). step-3의 문제가 돌아왔다.",
    ),
    7: dict(
        title="쿠키 요청은 출처를 확인한다",
        problem="쿠키 자동 전송(CSRF).",
        solution="SameSite=Lax(브라우저), CORS(브라우저 + CorsFilter), Origin 검사(서버) 세 겹으로 막는다.",
        terms=[
            ("CSRF", "다른 사이트가 사용자 브라우저로 우리 서버에 요청을 보내게 하는 공격. 쿠키가 자동으로 붙어서 성립한다."),
            ("SameSite=Lax", "다른 사이트에서 시작된 POST 등에는 쿠키를 붙이지 않는다. 링크 이동 같은 GET은 허용한다."),
            ("Origin 헤더", "요청을 시작한 페이지의 출처(scheme+host+port). 브라우저가 붙이며 JS로 바꿀 수 없다."),
            ("CORS", "다른 출처의 JS가 우리 서버를 호출해도 되는지 정하는 규칙. 허락 안 된 출처의 요청은 CorsFilter가 403으로 거절한다."),
            ("HandlerInterceptor", "컨트롤러 실행 직전(preHandle)에 끼어드는 Spring MVC 장치."),
        ],
        flow=["POST /token/refresh-cookie",
              "  CorsFilter(허용 안 된 Origin 403) → CookieOriginConfig(Origin 없음 403) → AuthController"],
        files={
            "auth/CookieAuthWriter.java": ("브라우저 쪽 방어.", {
                "refreshCookie": "SameSite=Lax 속성을 추가한다.",
            }),
            "config/CookieOriginConfig.java": ("서버 쪽 방어. Origin이 없는 요청까지 막는다.", {
                "addInterceptors": "Origin 검사 인터셉터를 쿠키 경로 3개에만 등록한다.",
                "preHandle": "POST 요청의 Origin 헤더가 없거나 허용 목록 밖이면 403(ForbiddenException). 컨트롤러 실행 전에 동작한다.",
            }),
            "config/SecurityCorsConfig.java": ("허용 출처 목록.", {
                "addCorsMappings": "/api/**에 대해 http://127.0.0.1:8000만 허용하고, 허용 메서드와 헤더(Content-Type, Authorization)를 정한다.",
            }),
            "config/SecurityConfig.java": ("csrf.disable()은 그대로 둔다. 쿠키를 쓰는 곳은 3개 경로뿐이고 거기는 Origin 검사로 막는다.", {
                "securityFilterChain": "cors(withDefaults)로 CORS 설정을 필터 체인에 연결하고, 브라우저 사전 요청(OPTIONS)을 허용한다.",
            }),
        },
        tests=["cookieRequestWithoutOriginIsRejectedByOriginCheck", "cookieRequestFromOtherOriginIsRejectedByCors",
               "corsAllowsOnlyFrontOrigin"],
        next="서버가 refresh를 저장하지 않아, 이미 복사된 refresh는 로그아웃해도 7일간 쓸 수 있다.",
    ),
    8: dict(
        title="refresh를 서버에 저장하고, 쓸 때마다 바꾼다 (회전)",
        problem="복사된 refresh를 서버가 폐기할 수 없다.",
        solution="refresh의 jti를 DB에 저장한다. 재발급하면 옛 것은 폐기하고 새 것을 저장한다. "
                 "폐기된 refresh가 다시 오면 탈취로 보고 그 회원의 refresh를 전부 폐기한다.",
        terms=[
            ("refresh 회전", "재발급할 때마다 refresh도 새로 주고 옛 것은 폐기하는 방식."),
            ("재사용 탐지", "폐기된 refresh가 다시 오면 누군가 복사본을 가진 것이므로 그 회원의 refresh를 전부 폐기한다."),
            ("비관적 잠금(PESSIMISTIC_WRITE)", "조회하면서 그 행을 잠가, 같은 행을 보려는 다른 트랜잭션을 기다리게 한다."),
            ("noRollbackFor", "지정한 예외가 나도 트랜잭션을 롤백하지 않는다. 401을 던지면서도 '전부 폐기'는 저장하려고 쓴다."),
        ],
        flow=["POST /token/refresh {R1} → R1 폐기, R2 저장 → 200",
              "POST /token/refresh {R1} → 이미 폐기됨 → 그 회원의 refresh 전부 폐기 → 401 (R2도 401)",
              "POST /logout-cookie (R2) → R2 폐기 + 쿠키 삭제"],
        files={
            "domain/RefreshToken.java": ("jti, loginId, 만료, 폐기 여부.", {
                "revoke": "폐기 표시. 행을 지우지 않고 남겨 두어야 '이미 쓴 refresh가 다시 왔다'를 알아챌 수 있다.",
            }),
            "repository/RefreshTokenRepository.java": ("메서드 이름으로 쿼리를 만든다(Spring Data JPA).", {
                "findByJti": "jti로 저장된 refresh를 찾는다. 행 잠금으로 같은 jti의 동시 요청을 줄 세워 한 요청만 통과시킨다.",
                "findAllByLoginIdAndRevokedFalse": "재사용을 탐지했을 때 그 회원의 살아 있는 refresh를 모두 찾아 폐기하는 데 쓴다.",
            }),
            "service/AuthService.java": ("발급할 때 저장하고, 재발급할 때 회전한다.", {
                "refresh": "refresh 검증 → DB에서 jti 조회(없으면 401) → 이미 폐기됐으면 재사용으로 보고 전부 폐기 후 401 → 아니면 폐기하고 새 쌍 발급.",
                "logout": "쿠키의 refresh를 검증해 DB에서 폐기한다. 이미 잘못된 토큰이면 조용히 넘어간다.",
                "tokensFor": "새 refresh의 jti와 만료 시각을 DB에 저장한 뒤 한 쌍을 돌려준다.",
            }),
            "controller/AuthController.java": ("로그아웃이 진짜 로그아웃이 된다.", {
                "logoutCookie": "쿠키의 refresh를 서버에서 폐기(authService.logout)한 뒤 쿠키를 지운다.",
            }),
        },
        tests=["rotatedRefreshCannotBeReused", "reuseRevokesAllRefreshOfMember",
               "concurrentReuseLetsOnlyOneThrough", "logoutRevokesRefreshOnServer"],
        next=None,
    ),
}

CONCLUSION = (
    "세션 쿠키를 피해 토큰으로 갔지만, refresh를 지키려다 다시 쿠키를 쓰게 됐고, "
    "refresh를 회전·폐기하려다 다시 서버 상태(DB)가 필요해졌다. 최종 구조는 "
    "<b>매 요청은 무상태 access, 재발급만 서버가 기억하는 refresh 쿠키</b>인, 세션과 토큰을 섞은 형태다. "
    "HttpOnly는 refresh를 읽지 못하게 할 뿐 XSS 자체를 막지는 못한다."
)

SIG = re.compile(r"^\s{4,8}(?:@\w+(?:\([^)]*\))?\s+)*(?:public|protected|private)?\s*(?:static\s+)?(?:final\s+)?"
                 r"[\w<>\[\],.? ]+\s+(\w+)\s*\(")
NOT_METHOD = {"if", "for", "while", "switch", "catch", "return", "new", "throw"}


def git(*args, check=True):
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)
    if check and result.returncode != 0:
        raise SystemExit(result.stderr)
    return result.stdout if result.returncode == 0 else None


def path_of(key):
    return key if key.startswith("src/") or key.endswith(".gradle") else SRC + key


def method_bodies(text):
    found = {}
    if not text:
        return found
    lines = text.splitlines()
    for i, line in enumerate(lines):
        m = SIG.match(line)
        if not m or m.group(1) in NOT_METHOD or line.strip().split(" ")[0] in NOT_METHOD:
            continue
        depth, started, body = 0, False, []
        for j in range(i, len(lines)):
            body.append(lines[j])
            depth += lines[j].count("{") - lines[j].count("}")
            started = started or "{" in lines[j]
            if (started and depth <= 0) or (not started and lines[j].rstrip().endswith(";")):
                break
        found[m.group(1)] = "\n".join(body)
    return found


def changed_methods(step, path):
    if not path.endswith(".java"):
        return []
    old = git("show", f"step-{step - 1}:{path}", check=False) if step > 0 else None
    new = git("show", f"step-{step}:{path}", check=False)
    before, after = method_bodies(old), method_bodies(new)
    cls = Path(path).stem
    return [name for name, body in after.items() if name != cls and before.get(name) != body]


def is_noise(text):
    s = text.strip()
    return s.startswith("import ") or s.startswith("package ")


def squeeze(lines):
    out = []
    for kind, text in lines:
        if is_noise(text):
            continue
        if not text.strip() and out and not out[-1][1].strip():
            continue
        out.append((kind, text))
    while out and not out[0][1].strip():
        out.pop(0)
    return out


def diff_lines(prev, cur, path):
    raw = git("diff", "-U99999", prev, cur, "--", path).splitlines()
    lines, started = [], False
    for line in raw:
        if line.startswith("@@"):
            started = True
            continue
        if not started or line.startswith("\\"):
            continue
        lines.append(({"+": "add", "-": "del"}.get(line[:1], "ctx"), line[1:]))
    return squeeze(lines)


def collapse(lines):
    if len(lines) <= FULL_LIMIT:
        return lines
    keep = set()
    for i, (kind, _) in enumerate(lines):
        if kind != "ctx":
            keep.update(range(max(0, i - CONTEXT), min(len(lines), i + CONTEXT + 1)))
    out, skipped = [], 0
    for i, line in enumerate(lines):
        if i in keep:
            if skipped:
                out.append(("gap", f"⋯ 바뀌지 않은 {skipped}줄"))
                skipped = 0
            out.append(line)
        else:
            skipped += 1
    if skipped:
        out.append(("gap", f"⋯ 바뀌지 않은 {skipped}줄"))
    return out


def code_block(lines):
    rows = []
    for kind, text in lines:
        mark = {"add": "+", "del": "−", "ctx": " ", "plain": " ", "gap": ""}[kind]
        rows.append(f'<div class="l {kind}"><span class="m">{mark}</span>{html.escape(text) or "&nbsp;"}</div>')
    return '<div class="code">' + "".join(rows) + "</div>"


def file_status(prev, cur, path):
    if prev is None:
        return "A"
    out = git("diff", "--name-status", prev, cur, "--", path).strip()
    return out.split("\t")[0] if out else ""


def method_table(methods):
    if not methods:
        return ""
    rows = "".join(f"<tr><td><code>{html.escape(name)}()</code></td><td>{html.escape(desc)}</td></tr>"
                   for name, desc in methods.items())
    return f'<table class="mt"><tr><th>메서드</th><th>하는 일</th></tr>{rows}</table>'


def render_file(step, key, note, methods):
    path = path_of(key)
    cur, prev = f"step-{step}", (f"step-{step - 1}" if step > 0 else None)
    status = file_status(prev, cur, path)
    name = path.split("/")[-1]
    role = ROLES.get(key, "")
    role_html = f'<p class="role">{html.escape(role)}</p>' if role else ""
    note_html = f'<p class="note"><b>이번 단계</b>: {html.escape(note)}</p>'
    if status == "D":
        return f'<div class="file"><div class="fh"><span class="badge del">삭제</span><b>{name}</b></div>{role_html}{note_html}</div>'
    if status == "A":
        body = squeeze([("plain", t) for t in git("show", f"{cur}:{path}").splitlines()])
        badge = '<span class="badge new">새 파일</span>'
    elif status == "M":
        body = collapse(diff_lines(prev, cur, path))
        badge = '<span class="badge mod">변경</span>'
    else:
        raise SystemExit(f"step-{step}: {path} 에 변경이 없습니다")
    return (f'<div class="file"><div class="fh">{badge}<b>{name}</b></div>{role_html}{note_html}'
            f'{method_table(methods)}{code_block(body)}</div>')


def check(step, data):
    warnings = []
    for key, (_, methods) in data["files"].items():
        path = path_of(key)
        changed = changed_methods(step, path)
        missing = [m for m in changed if m not in methods]
        stale = [m for m in methods if m not in changed]
        if missing:
            warnings.append(f"step-{step} {key}: 설명 없는 메서드 {missing}")
        if stale:
            warnings.append(f"step-{step} {key}: 이번 단계에서 바뀌지 않은 메서드 설명 {stale}")
        if key not in ROLES:
            warnings.append(f"step-{step} {key}: ROLES에 역할 설명 없음")
    if step > 0:
        changed_files = git("diff", "--name-only", f"step-{step - 1}", f"step-{step}", "--", "src/main", "build.gradle").split()
        shown = {path_of(k) for k in data["files"]}
        for p in changed_files:
            stem = Path(p).stem if p.endswith(".java") else Path(p).name
            if p not in shown and stem not in data.get("minor", ""):
                warnings.append(f"step-{step} {p}: 본문에도 '그 밖에 바뀐 파일'에도 없음")
    for w in warnings:
        print("경고:", w, file=sys.stderr)


def render_step(step, data):
    check(step, data)
    row = ROADMAP[step]
    head = (f'<section class="step"><h2><span class="no">step-{step}</span>{data["title"]}</h2>'
            f'<table class="sum"><tr><th>이전 문제</th><td>{html.escape(data["problem"])}</td></tr>'
            f'<tr><th>해결</th><td>{html.escape(data["solution"])}</td></tr>'
            f'<tr><th>얻은 것</th><td>{row[3]}</td></tr><tr><th>잃은 것</th><td>{row[4]}</td></tr></table>')
    terms = "".join(f"<dt>{html.escape(t)}</dt><dd>{html.escape(d)}</dd>" for t, d in data["terms"])
    terms_html = f'<div class="terms"><div class="tt">처음 나오는 용어</div><dl>{terms}</dl></div>'
    cmd = "git checkout step-0" if step == 0 else f"git diff step-{step - 1} step-{step}"
    flow = '<div class="flow">' + "<br>".join(html.escape(f) for f in data["flow"]) + "</div>"
    files = "".join(render_file(step, k, note, methods) for k, (note, methods) in data["files"].items())
    minor = f'<p class="minor"><b>그 밖에 바뀐 파일</b>: {html.escape(data["minor"])}</p>' if data.get("minor") else ""
    tests = ", ".join(f"<code>{t}</code>" for t in data["tests"])
    tail = f'<p class="next"><b>남은 문제 → step-{step + 1}</b>: {html.escape(data["next"])}</p>' if data["next"] else ""
    return (head + terms_html + f'<p class="cmd"><code>{cmd}</code></p><h3>요청 흐름</h3>{flow}<h3>코드</h3>{files}{minor}'
            f'<p class="tests"><b>확인할 테스트</b> (StepTest): {tests}</p>{tail}</section>')


def roadmap_table():
    rows = "".join(f"<tr><td><b>step-{r[0]}</b></td>" + "".join(f"<td>{c}</td>" for c in r[1:]) + "</tr>" for r in ROADMAP)
    return ("<table class='road'><tr><th>단계</th><th>문제</th><th>해결</th><th>얻은 것</th><th>잃은 것</th></tr>"
            + rows + "</table>")


CSS = """
@page { size: A4; margin: 14mm 13mm; }
* { box-sizing: border-box; }
body { font-family: 'Apple SD Gothic Neo', sans-serif; font-size: 10pt; color: #1d1d1f; line-height: 1.5; }
h1 { font-size: 22pt; margin: 0 0 4mm; }
h2 { font-size: 15pt; border-bottom: 2px solid #1d1d1f; padding-bottom: 2mm; margin: 0 0 3mm; }
h2 .no { background: #1d1d1f; color: #fff; padding: 1px 8px; border-radius: 4px; margin-right: 8px; font-size: 12pt; }
h3 { font-size: 11pt; margin: 4mm 0 1.5mm; }
.step { break-before: page; }
table { border-collapse: collapse; width: 100%; }
.sum th { width: 22mm; text-align: left; background: #f2f2f4; }
.sum th, .sum td, .road th, .road td, .mt th, .mt td { border: 1px solid #d2d2d7; padding: 3px 6px; vertical-align: top; }
.road { font-size: 9pt; } .road th, .mt th { background: #f2f2f4; }
.mt { font-size: 8.8pt; margin: 1mm 0 1.5mm; } .mt td:first-child { width: 38mm; white-space: nowrap; }
.mt tr { break-inside: avoid; }
.terms { margin: 3mm 0; border: 1px solid #c7d2fe; background: #f5f7ff; border-radius: 4px; padding: 2mm 3mm; break-inside: avoid; }
.terms .tt { font-weight: bold; font-size: 9.5pt; margin-bottom: 1mm; color: #3730a3; }
.terms dl { margin: 0; font-size: 9pt; } .terms dt { font-weight: bold; margin-top: 1mm; }
.terms dd { margin: 0 0 0 3mm; color: #3a3a3c; }
.cmd { margin: 2mm 0; color: #555; }
code { font-family: Menlo, monospace; font-size: 8.6pt; background: #f2f2f4; padding: 0 3px; border-radius: 3px; }
.flow { font-family: Menlo, monospace; font-size: 8.4pt; background: #f7f7f9; border-left: 3px solid #8e8e93; padding: 2mm 3mm; }
.file { margin: 4mm 0 5mm; }
.fh { font-size: 10.5pt; margin-bottom: 1mm; break-after: avoid; }
.badge { font-size: 8pt; padding: 1px 6px; border-radius: 3px; margin-right: 6px; color: #fff; }
.badge.new { background: #1f7a3a; } .badge.mod { background: #9a6700; } .badge.del { background: #b42318; }
.role { margin: 0; color: #6e6e73; font-size: 9pt; }
.note { margin: 0 0 1.5mm; color: #1d1d1f; font-size: 9.3pt; }
.code { font-family: Menlo, monospace; font-size: 7.6pt; line-height: 1.38; border: 1px solid #d2d2d7; border-radius: 4px; overflow: hidden; }
.l { white-space: pre-wrap; padding: 0 4px; break-inside: avoid; }
.l .m { display: inline-block; width: 1.4em; color: #8e8e93; user-select: none; }
.l.add { background: #e6f4ea; } .l.add .m { color: #1f7a3a; font-weight: bold; }
.l.del { background: #fdecea; color: #7a1f1f; } .l.del .m { color: #b42318; font-weight: bold; }
.l.gap { background: #f2f2f4; color: #8e8e93; font-style: italic; text-align: center; }
.minor { color: #3a3a3c; font-size: 9pt; }
.tests { font-size: 9pt; }
.next { background: #fff4e5; border-left: 3px solid #9a6700; padding: 2mm 3mm; }
.legend span { display: inline-block; padding: 0 6px; margin-right: 6px; font-family: Menlo, monospace; font-size: 8pt; }
.toc li { margin: 1mm 0; }
.conclusion { background: #f2f2f4; padding: 3mm 4mm; border-radius: 4px; }
"""


def intro():
    toc = "".join(f"<li><b>step-{i}</b> {STEPS[i]['title']}</li>" for i in STEPS)
    return (
        "<h1>security-steps 비교 학습</h1>"
        "<p>검증이 하나도 없는 글 API에서 시작해, 문제를 하나씩 해결하며 JWT + HttpOnly 쿠키 + refresh 회전 구조까지 간다. "
        "각 장은 한 브랜치이고, 코드는 <b>이전 단계와 비교한 변경</b>을 보여준다.</p>"
        "<h3>읽는 법</h3>"
        "<ul><li>각 장은 <b>요약 표 → 처음 나오는 용어 → 요청 흐름 → 코드</b> 순서다.</li>"
        "<li>파일마다 회색 글씨로 <b>그 클래스의 역할</b>, 그 아래에 <b>이번 단계에서 바뀐 이유</b>, "
        "그리고 이번 단계에서 새로 생기거나 바뀐 <b>메서드가 하는 일</b>을 표로 적었다.</li>"
        "<li class='legend'><span class='l add'>+ 추가된 줄</span><span class='l del'>− 삭제된 줄</span>"
        "<span class='l gap'>⋯ 생략</span> 새 파일은 전체 코드, 변경 파일은 바뀐 줄 주변만 보여준다. import와 package 줄은 생략했다.</li>"
        "<li>같은 내용을 직접 보려면 <code>git diff step-N step-N+1</code>.</li></ul>"
        f"<h3>로드맵</h3>{roadmap_table()}<h3>목차</h3><ol start='0' class='toc'>{toc}</ol>"
    )


def outro():
    return (
        "<section class='step'><h2>결론</h2>"
        f"<p class='conclusion'>{CONCLUSION}</p>"
        "<h3>남은 과제</h3><table class='road'><tr><th>과제</th><th>지금 상태</th><th>방향</th></tr>"
        "<tr><td>XSS 자체 방어</td><td>HttpOnly는 읽기만 막음</td><td>출력 이스케이프, CSP</td></tr>"
        "<tr><td>로그아웃 후 access</td><td>최대 15분 유효</td><td>감수하거나 블랙리스트</td></tr>"
        "<tr><td>만료된 refresh 행</td><td>DB에 계속 쌓임</td><td>스케줄러로 정리</td></tr>"
        "<tr><td>여러 기기 로그인</td><td>재사용 탐지 시 전부 폐기</td><td>기기별 refresh 관리</td></tr>"
        "<tr><td>로그인 시도 제한</td><td>없음</td><td>실패 횟수 제한·지연</td></tr>"
        "<tr><td>운영 환경</td><td>Secure=false, http</td><td>Secure=true, HTTPS</td></tr></table>"
        "<h3>다음 과정 예고</h3><ul>"
        "<li>비밀번호를 우리 서버가 아예 받지 않는다면, 누가 신원을 확인할까?</li>"
        "<li>카카오가 \"이 사람 맞다\"고 해 주면, 그 사람은 우리 회원 테이블의 누구일까?</li>"
        "<li>그때도 지금 만든 access/refresh 구조를 그대로 쓸 수 있을까?</li></ul></section>"
    )


def main():
    body = intro() + "".join(render_step(i, d) for i, d in STEPS.items()) + outro()
    OUT_HTML.write_text(f"<!doctype html><html lang='ko'><head><meta charset='utf-8'><title>security-steps 비교 학습</title>"
                        f"<style>{CSS}</style></head><body>{body}</body></html>", encoding="utf-8")
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                    f"--print-to-pdf={OUT_PDF}", OUT_HTML.as_uri()], check=True, capture_output=True)
    print(OUT_PDF)


if __name__ == "__main__":
    main()
