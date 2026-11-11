# Codex 칠판 호환성 진단

Android 11 ARM64 기기에서 공식 Codex app-server 실행을 점검하는 **시험 APK**다. 버전 0.5에는 ChatGPT 기기 코드 로그인과 텍스트 대화 실험이 포함되어 있지만 실제 기기에서 아직 검증되지 않았다. 파일 편집과 명령 실행은 지원하지 않는다.

1. `dist/codex-board-diagnostic.apk`를 설치한다.
2. 앱을 열어 `진단 시작`을 누르고 완료될 때까지 기다린다.
3. `결과 복사`를 눌러 진단 결과를 전달한다.

진단 화면은 Android 시스템의 DNS 및 HTTPS 연결을 확인한다. 이는 Codex 자체의 DNS 또는 로그인 성공 여부를 보장하지 않는다. 로그인·대화 시험 화면은 사용자가 로그인할 때 Codex의 계정 인증 정보를 앱 전용 저장소에 저장한다.

Linux용 Codex의 DNS 호환성 시험을 위해 Android 네트워크를 이용하는 인증된 로컬 HTTPS CONNECT 터널과 Android 시스템 CA 인증서 묶음을 사용한다. 터널은 OpenAI 및 ChatGPT의 HTTPS 호스트만 허용하고 TLS 내용을 복호화하지 않는다.

Codex 실행 파일: OpenAI Codex app-server `rust-v0.167.1`, 공식 `aarch64-unknown-linux-musl` 배포본. Android용 공식 배포본이 아니며 실행 가능성은 미검증이다. 소스와 라이선스: https://github.com/openai/codex/tree/rust-v0.167.1

빌드에는 Android SDK 플랫폼 30, build-tools 35.0.0, JDK 및 공식 Codex ARM64 바이너리가 필요하다. `ANDROID_SDK_ROOT`와 `CODEX_BINARY`를 지정하고 `bash build.sh`를 실행한다. 진단 APK는 로컬에서 생성한 테스트용 서명 키를 사용하며 정식 배포용이 아니다.
