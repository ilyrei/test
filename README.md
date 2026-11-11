# Codex 칠판 호환성 진단

Android 11 ARM64 기기에서 공식 Codex CLI 실행 파일의 기동 가능성을 점검하는 **초기 진단 APK**다. 채팅, 로그인, 파일 편집 및 명령 실행 기능은 아직 구현되지 않았다. 이 진단이 통과하더라도 Android 호환성, 로그인, 샌드박스와 명령 실행은 별도 검증이 필요하다.

1. `dist/codex-board-diagnostic.apk`를 설치한다.
2. 앱을 열어 `진단 시작`을 누르고 완료될 때까지 기다린다.
3. `결과 복사`를 눌러 진단 결과를 전달한다.

앱은 Android 시스템의 DNS 및 HTTPS 연결을 확인한다. 이는 Codex 자체의 DNS 또는 로그인 성공 여부를 보장하지 않는다. 진단은 계정 로그인, 토큰 또는 사용자 파일에 접근하지 않는다.

Codex 실행 파일: OpenAI Codex CLI `rust-v0.167.1`, 공식 `aarch64-unknown-linux-musl` 배포본. Android용 공식 배포본이 아니며 실행 가능성은 미검증이다. 소스와 라이선스: https://github.com/openai/codex/tree/rust-v0.167.1

빌드에는 Android SDK 플랫폼 30, build-tools 35.0.0, JDK 및 공식 Codex ARM64 바이너리가 필요하다. `ANDROID_SDK_ROOT`와 `CODEX_BINARY`를 지정하고 `bash build.sh`를 실행한다. 진단 APK는 로컬에서 생성한 테스트용 서명 키를 사용하며 정식 배포용이 아니다.
