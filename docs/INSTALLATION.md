# Установка и эксплуатация TCL

Пакет приложения: `pt.mvplisboa.sessionoverlay`. Проверенное устройство:
TCL Smart TV Pro / G08, Android 12 / API 31, `192.168.10.53:5555`, PS5 на HDMI 4.

## Подготовка и установка

Включите ADB debugging на телевизоре и подключите Mac к той же LAN.
Выполняйте команды из корня проекта:

```bash
./scripts/build.sh
./scripts/tv.sh connect
./scripts/tv.sh install
./scripts/tv.sh setup
```

В приложении нажмите Grant overlay permission и разрешите Display over other apps.
Если экран разрешения недоступен, диагностическая команда:

```bash
./scripts/tv.sh permission
```

После установки выполните:

```bash
./scripts/tv.sh activate
./scripts/tv.sh status
```

В статусе должны быть `service_running=true`, `foreground_service=true`,
`last_error=null`. Убедитесь, что полноэкранное окно и карточки скрыты перед сеансом.

## Особенность прошивки TCL

На этом TCL фоновый `startForeground` может быть отвергнут `TclAppBoot` с причиной
`default_borbid`, даже если приложение уже показывает окно. Поэтому наличие
изображения само по себе не доказывает foreground-статус.

`activate` временно открывает SetupActivity, запускает сервис, ждёт фактического
foreground и только затем нажимает Back. Если подтверждения нет, экран настроек
остаётся открытым для диагностики.

Для этого пакета на проверенном устройстве выдано дополнительное разрешение:

```bash
adb -s 192.168.10.53:5555 shell appops get pt.mvplisboa.sessionoverlay APP_AUTO_START
adb -s 192.168.10.53:5555 shell appops set pt.mvplisboa.sessionoverlay APP_AUTO_START allow
```

Название AppOps зависит от прошивки. Здесь `APP_AUTO_START` поддерживается,
`AUTO_START` — нет. Одного разрешения недостаточно для подтверждённого фонового
старта. Автозапуск после reboot в этой версии не реализован.

Проверка реального состояния Android:

```bash
adb -s 192.168.10.53:5555 shell dumpsys activity services pt.mvplisboa.sessionoverlay
```

В выводе должно быть `isForeground=true`.

## Команды

| Команда `./scripts/tv.sh …` | Действие |
|---|---|
| `connect`, `install`, `setup` | Подключить ADB, установить собранный APK, открыть администратора |
| `permission` | Выдать overlay AppOps для диагностики |
| `activate` | Запустить сервис из видимого администратора и вернуться назад |
| `start` | Передать запуск; после force-stop на TCL используйте `activate` |
| `status` | Получить JSON состояния |
| `show`, `hide` | Полный экран; Hide также отменяет таймер и демонстрацию |
| `show-delayed` | Полный экран через 10 секунд; Hide отменяет показ |
| `notice 900` | Одна карточка на 10 секунд; допустимы 900/300/60/30 |
| `demo` | Четыре карточки с интервалами 12 с, TIME IS UP на 48-й с, скрытие на 53-й |
| `timer 1200` | Новый локальный сеанс; длительность в секундах, максимум 24 часа |
| `extend 300` | Продлить текущий активный или истёкший сеанс |
| `cancel-timer` | Отменить таймер и скрыть окна |
| `stop` | Остановить сервис и убрать окна |
| `logs` | Читать логи MVPOverlay и ошибки AndroidRuntime |
| `emergency-hide` | Force-stop только нашего приложения |

Для другого телевизора задайте `MVP_ADB_SERIAL=IP:PORT`.
`install` использует результат сборки; если он удалён, выбирает единственный
готовый debug APK в `dist`. Явный путь можно задать через `MVP_APK_PATH`.

## Поведение и восстановление

Открытие администратора скрывает окна, сохраняя активный таймер. Hide отменяет
сеанс и все отложенные показы. Истёкший обычный тестовый сеанс удерживает
TIME IS UP до продления, отмены или Hide. Демонстрация убирает его автоматически.

```bash
./scripts/tv.sh hide
# Если команды сервиса недоступны:
./scripts/tv.sh emergency-hide
# Для следующего запуска:
./scripts/tv.sh activate
```

Команды адресованы только нашему пакету. `pm clear` и остановка TvOverlay не нужны.
ADB receiver присутствует только в debug APK и защищён системным разрешением `DUMP`.
В release APK receiver отсутствует; сервис всегда `exported=false`.

Поля `visible` и `notice_visible` означают окна, прикреплённые к WindowManager.
Фактическое перекрытие HDMI подтверждается визуально. При активном HDMI на этом
TCL `screencap` возвращает пустой результат.
