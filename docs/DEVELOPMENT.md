# Архитектура и разработка

## Сборка

Сборка использует JDK 17, SDK Platform 34, Build Tools 34.0.0, Android Gradle Plugin
8.5.2, Kotlin 1.9.24 и Gradle Wrapper 8.7. Совместимость зафиксирована по
[Android Developers](https://developer.android.com/build/releases/agp-8-5-0-release-notes).
Дистрибутив Gradle проверяется через `distributionSha256Sum`.

JDK и Android SDK устанавливаются отдельно от исходников. На macOS скрипт
использует `/usr/libexec/java_home -v 17`; можно явно задать `JAVA_HOME`.
Для SDK используйте `ANDROID_HOME` или локальный, исключённый из Git файл:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

```bash
./scripts/build.sh
# Произвольные Gradle-задачи:
./scripts/build.sh --console=plain :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
python3 scripts/package.py
```

На Windows настройте JDK/SDK и используйте `gradlew.bat` с теми же задачами.
Скрипты `*.sh` рассчитаны на Bash; Python-скрипты не требуют сторонних пакетов.

## Компоненты

| Файл | Ответственность |
|---|---|
| `SetupActivity.kt` | Разрешение overlay, администрирование, тестовый сеанс и продление |
| `OverlayService.kt` | Foreground-жизненный цикл, команды, планирование показов и таймера |
| `OverlayWindow.kt` | Непрозрачный полноэкранный ImageView через WindowManager |
| `WarningWindow.kt` | Угловые карточки, Canvas-отрисовка, анимация и автоскрытие |
| `SessionTimerEngine.kt` | Независимая от Android логика сроков и предупреждений |
| `OverlayState.kt` | Наблюдаемое состояние текущего процесса |
| `DebugControlReceiver.kt` | Debug-only мост ADB с разрешением DUMP |

Все команды и операции окон выполняются в главном Android-потоке. Задержанные
показы и демонстрация отменяются при Hide/Stop. Один тип окна имеет не более одного
прикреплённого View. Окна не получают фокус и не запускают Activity для показа.

Сервис подтверждает foreground через состояние Android. Уже работающему
foreground-сервису команды передаются через `startService`, без новых обязательств
фонового foreground-запуска. Ограничение TCL и ручная активация описаны отдельно
в [установке](INSTALLATION.md).

## Таймер

При подтверждении сеанса UTC-срок якорится к `elapsedRealtime`. Отсчёт основан
на сроке, а не на количестве обработанных тиков. Повторное подтверждение того же
ID и срока не повторяет предупреждения. Если тик опоздал и пересёк несколько
порогов, выбирается наиболее срочный. Продление заменяет старый срок и снимает
полноэкранное окно; отмена предотвращает запоздалые показы.

Состояния локального сеанса: `IDLE`, `ACTIVE`, `EXPIRED`. Демонстрация отдельно
использует `DEMO` и `DEMO_EXPIRED`. После перезапуска процесса сеанс не восстанавливается.

Статус содержит `service_running`, `foreground_service`, `visible`,
`notice_visible`, `notice_threshold_seconds`, `timer_state`, `session_id`,
`expires_at_utc_millis`, `remaining_seconds`, `demo_running`, `last_error`.
Ответ ADB подтверждает приём команды; завершение проверяйте последующим STATUS.

## Оформление

Единственный оригинал TIME IS UP хранится в
`app/src/main/res/drawable-nodpi/session_ended.png` (1672×941, без прозрачности).
`FIT_XY` заполняет текущие размеры окна.

Карточки отрисовываются нативно, без PNG-макетов. `CARD_SCALE=0.8` уменьшает
размеры и содержимое на 20%. Фон имеет alpha 190/185 из 255; текст непрозрачный.
Отступы 12 dp, пороги 900/300/60/30 секунд, показ 10 секунд. На проверенном TCL
размер — 499×265 px, отступы — 24 px.

## Границы текущей версии

Нет HTTP API, токена, boot receiver, внешней базы сеансов и ggLeap-интеграции.
Не добавлены имитации этих компонентов или пустые заглушки. Следующий этап —
защищённое LAN API с подтверждением фактических операций, затем серверное состояние,
reconciliation и проверенный сценарий запуска после reboot.

`targetSdk 32` оставлен для локального Android TV 12. Lint оставляет видимым
предупреждение `ExpiredTargetSdkVersion`. Перед публикацией требуются обновление
target, подходящий foreground service type и release signing. Ключ подписи и
секреты в исходники не включаются.
