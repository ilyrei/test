# Codex 칠판 호환성 진단

Android 11 ARM64 기기에서 Codex CLI를 실행하는 **시험 APK**다. 0.10은 빠져 있던 코드 실행 호스트를 추가했다. 실제 기기에서 호스트 실행, 명령 실행, 작업 공간 제한, 파일 작업과 Git은 아직 검증되지 않았다. 기존 로그인 데이터와 0.6 채팅 화면은 유지한다.

1. 릴리스의 `codex-board-v0.10-test.apk`를 설치한다. `dist`의 이전 APK는 0.6이다.
2. 기존 로그인은 `로그인·기존 대화`에서 확인한다.
3. `보호 모드 시작`으로 원본 CLI를 시작한다. 격리 기능이 Android에서 작동하는지는 확인이 필요하다.
4. 폴더를 가져오거나 내보낼 때는 실행 중인 Codex를 중지한다. 가져오기에서 이름이 같은 파일은 건너뛰며 내보내기는 새 폴더를 만든다.

진단 화면은 Android 시스템의 DNS 및 HTTPS 연결을 확인한다. 이는 Codex 자체의 DNS 또는 로그인 성공 여부를 보장하지 않는다. 로그인·대화 시험 화면은 사용자가 로그인할 때 Codex의 계정 인증 정보를 앱 전용 저장소에 저장한다.

Linux용 Codex의 DNS 호환성을 위해 Android 네트워크를 이용하는 인증된 로컬 HTTPS CONNECT 터널과 Android 시스템 CA 인증서 묶음을 사용한다. 채팅 모드는 OpenAI 및 ChatGPT 호스트로 제한하고, CLI 모드는 일반 HTTPS 호스트를 허용한다. TLS 내용은 복호화하지 않는다.

Codex 실행 파일: OpenAI Codex CLI 및 code-mode-host `rust-v0.167.1`, 공식 `aarch64-unknown-linux-musl` 배포본을 기반으로 한다. Android의 네이티브 라이브러리 이름 규칙에 맞춰 CLI 내부의 호스트 파일명 문자열을 `scripts/patch-host-name.py`로 변경한다. Android용 공식 배포본이 아니며 호환성은 미검증이다. 소스와 라이선스: https://github.com/openai/codex/tree/rust-v0.167.1. 터미널 구성 요소는 Termux `8629e632fcb95da272221be327db653fb24befe9` 소스에서 가져왔으며 관련 라이선스는 `vendor/licenses`에 포함한다.

빌드에는 Android SDK 플랫폼 35, build-tools 35.0.0, NDK 27.2.12479018, JDK 21, AndroidX annotation-jvm 1.9.1 및 공식 Codex와 code-mode-host ARM64 바이너리가 필요하다. `ANDROID_SDK_ROOT`, `ANDROID_NDK_ROOT`, `ANNOTATION_JAR`, `CODEX_BINARY`, `CODEX_HOST_BINARY`를 지정하고 `bash build.sh`를 실행한다. APK는 기존 시험판과 같은 테스트용 서명 키를 사용하며 정식 배포용이 아니다.
