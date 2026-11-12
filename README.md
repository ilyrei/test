# Codex 칠판 호환성 진단

Android 11 ARM64 기기에서 Codex CLI를 실행하는 **시험 APK**다. 0.11은 정적 ARM64 GNU Bash와 ripgrep을 포함하고 Codex의 기본 셸 탐색 경로를 설정한다. 실제 기기에서 새 셸 경로, 작업 공간 제한, 파일 작업과 Git은 아직 검증되지 않았다. 기존 로그인 데이터와 0.6 채팅 화면은 유지한다.

1. 릴리스의 `codex-board-v0.11-test.apk`를 설치한다. `dist`의 이전 APK는 0.6이다.
2. 기존 로그인은 `로그인·기존 대화`에서 확인한다.
3. `보호 모드 시작`으로 원본 CLI를 시작한다. 격리 기능이 Android에서 작동하는지는 확인이 필요하다.
4. 폴더를 가져오거나 내보낼 때는 실행 중인 Codex를 중지한다. 가져오기에서 이름이 같은 파일은 건너뛰며 내보내기는 새 폴더를 만든다.

진단 화면은 Android 시스템의 DNS 및 HTTPS 연결을 확인한다. 이는 Codex 자체의 DNS 또는 로그인 성공 여부를 보장하지 않는다. 로그인·대화 시험 화면은 사용자가 로그인할 때 Codex의 계정 인증 정보를 앱 전용 저장소에 저장한다.

Linux용 Codex의 DNS 호환성을 위해 Android 네트워크를 이용하는 인증된 로컬 HTTPS CONNECT 터널과 Android 시스템 CA 인증서 묶음을 사용한다. 채팅 모드는 OpenAI 및 ChatGPT 호스트로 제한하고, CLI 모드는 일반 HTTPS 호스트를 허용한다. TLS 내용은 복호화하지 않는다.

Codex 실행 파일: OpenAI Codex CLI 및 code-mode-host `rust-v0.167.1`, 공식 `aarch64-unknown-linux-musl` 배포본을 기반으로 한다. Android의 네이티브 라이브러리 이름 규칙에 맞춰 CLI 내부의 호스트 파일명 문자열을 `scripts/patch-host-name.py`로 변경한다. Android용 공식 배포본이 아니며 호환성은 미검증이다. 소스와 라이선스: https://github.com/openai/codex/tree/rust-v0.167.1. 터미널 구성 요소는 Termux `8629e632fcb95da272221be327db653fb24befe9` 소스에서 가져왔으며 관련 라이선스는 `vendor/licenses`에 포함한다.

빌드에는 Android SDK 플랫폼 35, build-tools 35.0.0, NDK 27.2.12479018, JDK 21, AndroidX annotation-jvm 1.9.1 및 Codex, code-mode-host, Bash, ripgrep ARM64 바이너리가 필요하다. 경로는 `ANDROID_SDK_ROOT`, `ANDROID_NDK_ROOT`, `ANNOTATION_JAR`, `CODEX_BINARY`, `CODEX_HOST_BINARY`, `CODEX_BASH_BINARY`, `CODEX_RG_BINARY`로 지정한다. APK는 기존 시험판과 같은 테스트용 서명 키를 사용하며 정식 배포용이 아니다.

정적 Bash는 [GNU Bash 5.2.37 소스](https://ftp.gnu.org/gnu/bash/bash-5.2.37.tar.gz)와 [musl cross-toolchain 20261001](https://github.com/cross-tools/musl-cross/releases/tag/20261001)을 사용해 `scripts/build-bash.sh`로 생성한다. 배포 바이너리는 GCC 16.2.0, musl 1.2.6으로 빌드했다. ripgrep은 [15.2.0 ARM64 musl 공식 배포본](https://github.com/BurntSushi/ripgrep/releases/tag/15.2.0)을 사용한다. 포함된 소프트웨어의 라이선스는 APK `assets`와 `vendor/licenses`에서 확인할 수 있다.
