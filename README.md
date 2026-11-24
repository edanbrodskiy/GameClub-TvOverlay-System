# MVP Session Overlay

Android TV-приложение для MVP Lisboa: полноэкранный TIME IS UP поверх HDMI,
локальный таймер сеанса и предупреждения в правом верхнем углу.

**Версия:** `0.2.1-compact` · **Пакет:** `pt.mvplisboa.sessionoverlay`.
Показ поверх PS5 на HDMI 4 и восстановление изображения после скрытия подтверждены
на TCL Smart TV Pro, Android 12. Подробности — в [результатах испытаний](docs/TEST_RESULTS.md).

## Быстрый запуск

Нужны JDK 17, Android SDK Platform 34, Build Tools 34.0.0. Для работы с ТВ — ADB,
для скриптов испытаний и упаковки — Python 3.10+ без дополнительных зависимостей.

Откройте **эту папку** в Android Studio или выполните:

```bash
./scripts/build.sh
python3 scripts/package.py
```

Скрипт сборки запускает Gradle Wrapper: сборку debug APK, Android Lint и 11 тестов
логики таймера. На macOS автоматически выбирается установленный JDK 17.
SDK задаётся через `ANDROID_HOME` либо локальный `local.properties` с `sdk.dir`.
Стандартный путь SDK на macOS определяется автоматически.

Готовые файлы после упаковки:

- `dist/mvp-session-overlay-0.2.1-compact-debug.apk` — APK для установки.
- `dist/mvp-session-overlay-0.2.1-compact-source.zip` — исходники без SDK, кэшей и локальных путей.
- `dist/SHA256SUMS` — контрольные суммы.

## Установка и управление

По умолчанию команды адресованы `192.168.10.53:5555`. Другое устройство:
`export MVP_ADB_SERIAL=IP:PORT`.

```bash
./scripts/tv.sh connect
./scripts/tv.sh install
./scripts/tv.sh permission
./scripts/tv.sh activate
./scripts/tv.sh status
```

`activate` открывает администратора, ждёт подтверждения foreground-сервиса и
возвращается к предыдущему экрану. Это требуется на проверенной прошивке TCL.
Выдача overlay AppOps — диагностический способ; разрешение также доступно через
экран приложения. Подробная настройка — в [инструкции установки](docs/INSTALLATION.md).

```bash
./scripts/tv.sh demo          # Четыре предупреждения, финальный экран, автоскрытие через 53 с.
./scripts/tv.sh timer 1200    # Локальный сеанс на 20 минут.
./scripts/tv.sh extend 300    # Продлить на 5 минут и убрать экран окончания.
./scripts/tv.sh notice 900    # Preview предупреждения: 900 / 300 / 60 / 30 секунд.
./scripts/tv.sh show
./scripts/tv.sh hide          # Скрыть все окна и отменить тестовый таймер/демонстрацию.
```

Предупреждения срабатывают за 15 минут, 5 минут, 1 минуту и 30 секунд, остаются
на экране 10 секунд. Карточки уменьшены на 20%, фон примерно на 25% прозрачен,
текст непрозрачный. На текущем TCL размер карточки — 499×265 px.

## Структура

```text
app/src/main/       Приложение, ресурсы, таймер и окна
app/src/debug/      Защищённое управление через ADB, только в debug APK
app/src/test/       JVM-тесты логики таймера
gradle/wrapper/     Gradle Wrapper 8.7 с проверкой SHA-256
scripts/           Сборка, упаковка, ADB, испытания
docs/              Установка, архитектура, результаты и исходное руководство
dist/              Сгенерированные APK, исходный архив и контрольные суммы
```

## Документация и состояние

- [Установка и диагностика TCL](docs/INSTALLATION.md).
- [Архитектура и разработка](docs/DEVELOPMENT.md).
- [Автоматические и аппаратные испытания](docs/TESTING.md).
- [Результаты проверок](docs/TEST_RESULTS.md).
- [История изменений](CHANGELOG.md).
- [Исходное техническое руководство](docs/reference/MVP_Lisboa_TCL_Overlay_Development_Guide.md).

Реализован автономный Android-прототип. REST API на 5002, API-токен, boot receiver,
серверное состояние сеансов и интеграция ggLeap остаются следующими этапами.
Локальный таймер пока не связан с оплатой; перезапуск процесса сбрасывает сеанс
и не восстанавливает блокировку. 30-минутное удержание и reboot ещё не проверены.

`minSdk 26`, `targetSdk 32`, `compileSdk 34`. Target выбран для локального TCL
Android 12; обновление target и правил foreground service требуется перед выпуском.
