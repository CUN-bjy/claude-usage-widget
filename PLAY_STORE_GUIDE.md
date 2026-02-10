# Google Play Store 퍼블리싱 가이드

이 문서는 Claude Usage Widget Android 앱을 Google Play Store에 출시하기 위한 단계별 가이드입니다.

---

## 전체 체크리스트

| # | 단계 | 상태 |
|---|------|------|
| 1 | Google Play Developer 계정 생성 | ⬜ |
| 2 | 릴리즈 키스토어(Keystore) 생성 | ⬜ |
| 3 | AAB (Android App Bundle) 빌드 | ⬜ |
| 4 | 개인정보처리방침(Privacy Policy) 웹 호스팅 | ⬜ |
| 5 | Play Store 스토어 등록정보 준비 | ⬜ |
| 6 | Play Console에서 앱 등록 및 업로드 | ⬜ |
| 7 | 콘텐츠 등급 설문 작성 | ⬜ |
| 8 | 타겟 연령층 및 광고 설정 | ⬜ |
| 9 | 출시 트랙 선택 및 배포 | ⬜ |

---

## 1단계: Google Play Developer 계정 생성

1. [Google Play Console](https://play.google.com/console) 접속
2. **$25 일회성 등록비** 결제
3. 개발자 정보 입력 (이름, 이메일, 전화번호)
4. 본인 인증 완료 (최대 48시간 소요될 수 있음)

> 개인 계정과 조직 계정 중 선택할 수 있습니다. 개인 프로젝트라면 개인 계정으로 충분합니다.

---

## 2단계: 릴리즈 키스토어 생성

Play Store에 올리려면 앱에 서명해야 합니다. **이 키스토어는 절대 잃어버리면 안 됩니다.**

### 키스토어 생성

```bash
keytool -genkey -v \
  -keystore claude-usage-release.keystore \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias claude-usage-key
```

프롬프트에 따라 비밀번호와 정보를 입력합니다:
- **Keystore password**: 안전한 비밀번호 설정
- **Key alias**: `claude-usage-key` (위 명령어에서 이미 설정)
- **Key password**: 안전한 비밀번호 설정
- 이름, 조직, 도시, 국가 등 입력

### 키스토어 보관

- 키스토어 파일(`claude-usage-release.keystore`)을 **안전한 곳에 백업**
- **절대 Git에 커밋하지 마세요** (`.gitignore`에 포함되어 있는지 확인)
- 비밀번호를 별도로 기록해두세요

---

## 3단계: AAB (Android App Bundle) 빌드

Play Store는 APK 대신 **AAB 형식**을 요구합니다.

### 환경 변수 설정

```bash
export KEYSTORE_FILE=/path/to/claude-usage-release.keystore
export KEYSTORE_PASSWORD=your_keystore_password
export KEY_ALIAS=claude-usage-key
export KEY_PASSWORD=your_key_password
```

### AAB 빌드

```bash
cd android
./gradlew bundleRelease
```

빌드 완료 후 AAB 파일 위치:
```
android/app/build/outputs/bundle/release/app-release.aab
```

### 빌드 확인

```bash
# AAB 파일이 생성되었는지 확인
ls -la app/build/outputs/bundle/release/
```

---

## 4단계: 개인정보처리방침 웹 호스팅

Play Store는 **접근 가능한 URL**로 된 개인정보처리방침을 요구합니다.

이미 `PRIVACY_POLICY.md` 파일이 있으므로, 다음 중 하나의 방법으로 웹에 공개합니다:

### 방법 A: GitHub Pages 활용 (가장 간단)

`PRIVACY_POLICY.md`가 이미 리포지토리에 있으므로:

```
https://github.com/CUN-bjy/claude-usage-widget/blob/main/PRIVACY_POLICY.md
```

이 URL을 그대로 사용할 수 있습니다.

### 방법 B: GitHub Pages 정식 호스팅

1. 리포지토리 Settings → Pages
2. Source: `main` branch, `/docs` 또는 `/root` 선택
3. `PRIVACY_POLICY.md`를 해당 경로에 배치
4. URL: `https://cun-bjy.github.io/claude-usage-widget/PRIVACY_POLICY`

---

## 5단계: Play Store 스토어 등록정보 준비

Play Console에 입력해야 할 정보들입니다:

### 기본 정보

| 항목 | 값 |
|------|-----|
| **앱 이름** | ClaudeMeter |
| **부제목** (30자 이내) | Don't Waste a Single Token |
| **간단한 설명** (80자 이내) | Squeeze your productivity. Monitor your Claude.ai usage in real-time. |
| **자세한 설명** (4000자 이내) | 아래 참고 |
| **카테고리** | 도구 (Tools) |
| **이메일** | 본인 이메일 주소 |

### 자세한 설명 (예시)

```
ClaudeMeter는 Claude.ai의 사용량을 실시간으로 확인할 수 있는 안드로이드 앱입니다.

주요 기능:
• 실시간 사용량 모니터링 - 세션 및 주간 사용량을 한눈에 확인
• 홈 화면 위젯 - 앱을 열지 않아도 사용량 확인 가능
• 알림 기능 - 사용량 한도에 도달하기 전 미리 알림
• 자동 새로고침 - 백그라운드에서 주기적으로 사용량 업데이트

특징:
• 개인정보 보호 - 모든 데이터는 기기에만 저장되며 제3자에게 전송되지 않습니다
• 오픈소스 - GitHub에서 소스코드를 확인할 수 있습니다
• 가벼운 앱 - 최소한의 리소스만 사용합니다

참고: 이 앱은 Anthropic과 공식적으로 관련이 없는 비공식 도구입니다.
Claude.ai 계정의 세션 키가 필요합니다.
```

### 필수 그래픽 자산

| 자산 | 규격 | 설명 |
|------|------|------|
| **앱 아이콘** | 512 x 512px, PNG (32bit) | 이미 mipmap에 있지만, 512px 고해상도 버전 필요 |
| **피처 그래픽** | 1024 x 500px, PNG/JPEG | 스토어 상단에 표시되는 배너 이미지 |
| **스크린샷** | 최소 2장, 폰 기준 최소 320px ~ 최대 3840px | 앱의 주요 화면 캡처 |

#### 스크린샷 준비 방법

1. 에뮬레이터 또는 실기기에서 앱 실행
2. 다음 화면들을 캡처:
   - **로그인 화면**
   - **메인 대시보드** (사용량 표시)
   - **홈 화면 위젯**
   - **알림** (선택사항)
3. 권장 해상도: **1080 x 1920px** (Pixel 기기 기준)

---

## 6단계: Play Console에서 앱 등록

1. [Play Console](https://play.google.com/console) 접속
2. **"앱 만들기"** 클릭
3. 기본 정보 입력:
   - 앱 이름: `ClaudeMeter`
   - 기본 언어: 한국어 또는 영어
   - 앱/게임: **앱**
   - 무료/유료: **무료**
4. 선언 체크박스 모두 동의
5. **앱 만들기** 완료

### 스토어 등록정보 입력

대시보드 왼쪽 메뉴에서:

1. **기본 스토어 등록정보** → 5단계에서 준비한 내용 입력
2. **그래픽** → 아이콘, 스크린샷, 피처 그래픽 업로드

---

## 7단계: 콘텐츠 등급 설문

Play Console 대시보드 → **콘텐츠 등급**:

1. **설문 시작** 클릭
2. 카테고리: **유틸리티** 선택
3. 대부분의 질문에 "아니오" 응답 (폭력, 성적 콘텐츠 등 없음)
4. **설문 저장** → **등급 계산** → **적용**

예상 등급: **전체이용가 (Everyone)**

---

## 8단계: 앱 콘텐츠 설정

Play Console → **앱 콘텐츠** 섹션에서 모두 작성:

### 개인정보처리방침
- 4단계에서 호스팅한 URL 입력

### 광고
- **앱에 광고가 포함되어 있지 않습니다** 선택

### 앱 액세스 권한
- **로그인 자격 증명 필요** 선택
- 설명: "Claude.ai 계정의 세션 키가 필요합니다. 테스트 계정을 제공할 수 없습니다."
- 또는 **특별한 액세스 권한 없이 모든 기능 사용 가능** (Google이 세션 키를 이해하지 못할 수 있으므로 이 옵션이 심사에 유리할 수 있음)

### 타겟 연령층
- **18세 이상** 선택 (Claude.ai 계정이 필요하므로)

### 데이터 보안 섹션

| 질문 | 답변 |
|------|------|
| 앱이 필수 사용자 데이터를 수집하나요? | 예 (세션 키) |
| 데이터가 암호화되어 전송되나요? | 예 (HTTPS) |
| 사용자가 데이터 삭제를 요청할 수 있나요? | 예 (앱 삭제 또는 로그아웃) |
| 데이터가 제3자와 공유되나요? | 아니오 |

데이터 유형:
- **인증 정보 (Auth credentials)** - 수집됨, 기기에만 저장

---

## 9단계: 릴리즈 및 출시

### 내부 테스트 (권장 첫 단계)

1. Play Console → **테스트** → **내부 테스트**
2. **새 릴리즈 만들기**
3. **Play 앱 서명** 동의 (Google이 앱 서명을 관리)
4. 3단계에서 빌드한 `app-release.aab` 업로드
5. 릴리즈 이름: `1.0.0`
6. 릴리즈 노트 입력:
   ```
   첫 번째 릴리즈
   - Claude.ai 사용량 실시간 모니터링
   - 홈 화면 위젯 지원
   - 알림 기능
   ```
7. **검토 시작**

### 프로덕션 출시

내부 테스트 확인 후:

1. Play Console → **프로덕션**
2. **새 릴리즈 만들기**
3. 내부 테스트에서 사용한 AAB를 프로모션하거나 다시 업로드
4. **검토를 위해 릴리즈 제출**

> Google의 앱 검토는 보통 **1~7일** 소요됩니다. 첫 출시의 경우 더 오래 걸릴 수 있습니다.

---

## 주의사항

### 반드시 확인할 것

1. **targetSdk 요구사항**: 현재 targetSdk 34로 설정되어 있어 문제 없음
2. **앱 서명**: Play App Signing을 사용하면 Google이 최종 서명을 관리합니다. 업로드 키(여러분이 만든 키스토어)만 관리하면 됩니다
3. **비공식 앱 명시**: 앱 설명에 Anthropic과 공식 관계가 없음을 반드시 명시하세요
4. **상표 관련**: "Claude"라는 이름은 Anthropic의 상표일 수 있습니다. 심사에서 거부될 경우 앱 이름 변경이 필요할 수 있습니다

### 거부될 수 있는 사유

- **상표 침해**: "Claude" 이름 사용 → 앱 설명에 비공식 도구임을 반드시 명시
- **로그인 방식**: 세션 키 직접 입력 방식은 Google이 보안 문제로 지적할 수 있음
- **테스트 불가**: Google 심사팀이 앱을 테스트할 수 없으면 거부될 수 있음

### 권장 사항

- 앱 이름을 `Claude Usage Widget (Unofficial)` 등으로 변경 고려
- 첫 출시는 **내부 테스트 → 비공개 테스트 → 프로덕션** 순서 권장
- Play Store 등록 전에 실기기에서 충분히 테스트

---

## 빠른 명령어 요약

```bash
# 1. 키스토어 생성
keytool -genkey -v -keystore claude-usage-release.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 -alias claude-usage-key

# 2. 환경 변수 설정
export KEYSTORE_FILE=$(pwd)/claude-usage-release.keystore
export KEYSTORE_PASSWORD=your_password
export KEY_ALIAS=claude-usage-key
export KEY_PASSWORD=your_password

# 3. AAB 빌드
cd android
./gradlew bundleRelease

# 4. 결과 확인
ls -la app/build/outputs/bundle/release/app-release.aab
```

---

## 출시 후 할 일

- [ ] 크래시 리포트 모니터링 (Play Console → Android Vitals)
- [ ] 사용자 리뷰 확인 및 응답
- [ ] targetSdk 업데이트 (Google의 연간 요구사항에 맞춰)
- [ ] 버전 업데이트 시 `versionCode` 증가 필수
