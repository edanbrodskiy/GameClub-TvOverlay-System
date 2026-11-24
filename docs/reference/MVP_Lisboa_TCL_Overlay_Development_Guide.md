# MVP Lisboa — TCL Android TV: полноэкранный HDMI-оверлей

**Техническое задание, архитектура, реализация, сборка APK, установка и испытания**  
Версия: 1.0 · 24 ноября 2026

## 1. Цель и подтверждённая исходная конфигурация

Создать отдельное Android TV-приложение `MVP Session Overlay`, которое показывает **непрозрачное изображение на весь экран поверх HDMI 4 (PlayStation 5)** после завершения оплаченного времени. При продлении времени изображение убирается, не переключая вход и не прерывая игру. Действующий TvOverlay остаётся для отсчёта времени.

| Компонент | Данные |
|---|---|
| Телевизор | TCL Android TV 12 |
| IP телевизора | `192.168.10.53` (закрепить DHCP reservation) |
| HDMI | HDMI 4 — PS5 |
| Mac-разработчик | macOS, локальный IP при тестировании `192.168.10.19` |
| ADB | `adb connect 192.168.10.53:5555` |
| TvOverlay | `com.tabdeveloper.tvoverlay`, REST API порт `5001` |
| Новый сервис | `pt.mvplisboa.sessionoverlay`, REST API порт `5002` |
| Ресурс | `image.png` — фирменный экран TIME IS UP |

**Подтверждено испытаниями:** TvOverlay выводит текст и PNG поверх PS5 HDMI, но в области уведомления справа сверху. **Не подтверждено:** полноэкранное системное окно стороннего APK поверх аппаратного HDMI-слоя конкретной модели TCL. Это главный технический риск; сначала выполняется proof-of-concept.

## 2. Функциональные требования

1. `POST /show` — показать полноэкранное изображение `TIME IS UP` (непрозрачное, без рамок, без Android UI).
2. `POST /hide` — скрыть экран и восстановить обзор HDMI.
3. `GET /status` — JSON с `visible`, `service_running`, `version`, `uptime_seconds`.
4. `GET /health` — `200 OK` при живом процессе.
5. `POST /session` — опциональный endpoint для установки `session_id`, `expires_at`, `state`; не нужен для первого прототипа.
6. Приоритет команд: `hide` должен срабатывать немедленно; повторные `show`/`hide` идемпотентны.
7. Восстановление после перезагрузки ТВ; при этом **не показывать блокировку автоматически без подтверждённого активного истёкшего сеанса**.
8. Таймер TvOverlay (порт 5001) и новое приложение (5002) независимы.
9. На экране нет кнопки закрытия для посетителя. У администратора есть API и резервная команда ADB.
10. Секретный API-токен; принимать запросы только из доверенной локальной сети. Никакой публикации порта в Интернет.

### Критерии приёмки

- Полноэкранная PNG видна поверх **активного** HDMI 4; нет полос с игрой по краям.
- `POST /hide` убирает окно менее чем за 1 секунду на стабильной LAN.
- Повторные запросы не создают дополнительные окна.
- Через 30 минут показа приложение не теряет окно и не падает.
- После смены HDMI → Android Home → HDMI 4 проверено, что поведение соответствует требованиям.
- При потере связи предусмотрено ручное снятие блокировки через ADB/меню администратора.

## 3. Архитектура

```text
GGLeap / локальная система клуба
          |
          v
Python Session Controller (авторитетное состояние, БД, логирование)
          |                       |
          | HTTP + X-API-Key      | HTTP
          v                       v
TCL Android TV :5002          TvOverlay :5001
MVP Session Overlay           Обновляемый таймер
          |
          v
Foreground Service + WindowManager
          |
          v
TYPE_APPLICATION_OVERLAY / ImageView (MATCH_PARENT)
          |
          v
Android TV compositor → HDMI 4 (требует реального теста)
```

**Не используйте Activity на весь экран** как основной метод: Activity может переключить телевизор с HDMI на Android UI. Нужен `WindowManager` с `TYPE_APPLICATION_OVERLAY` и разрешением `SYSTEM_ALERT_WINDOW`. Даже это **не гарантирует** перекрытия аппаратного HDMI на каждом TCL; проверяется на устройстве.

### Компоненты APK

- `SetupActivity.kt` — интерфейс администратора: статус разрешения, кнопки `Grant overlay`, `Start service`, `Show test`, `Hide test`, `Stop service`.
- `OverlayService.kt` — foreground service, владелец окна, обработка show/hide, сервер HTTP.
- `OverlayWindow.kt` — создание `ImageView` через `WindowManager`; `FLAG_NOT_FOCUSABLE`, `FLAG_NOT_TOUCH_MODAL`, `PixelFormat.OPAQUE`, `MATCH_PARENT`; при необходимости immersive UI.
- `ApiServer.kt` — компактный HTTP-сервер на `ServerSocket`, `127.0.0.1` или `0.0.0.0` для LAN; отдельный поток, лимит тела запроса, таймауты, токен.
- `BootReceiver.kt` — `BOOT_COMPLETED`, запуск только в рамках ограничений Android 12 и после настройки разрешений.
- `res/drawable-nodpi/session_ended.png` — изображение; `nodpi` предотвращает нежелательное масштабирование Android.
- `SharedPreferences` — токен и минимальное состояние; реальные сессии хранятся на сервере клуба.

## 4. Среда разработки на macOS

Установить Android Studio (актуальную стабильную), Android SDK Platform 32+ и Build Tools, JDK 17, Android Platform Tools. Для сборки использовать **Gradle Wrapper из проекта**, не системный Gradle. Android Gradle Plugin и Gradle брать совместимой пары из мастера Android Studio; не копировать случайные версии из интернета.

Проверка:

```bash
java -version
adb version
adb connect 192.168.10.53:5555
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell wm size
adb shell wm density
```

В Android Studio: **New Project → Empty Views Activity → Kotlin**, package `pt.mvplisboa.sessionoverlay`, Minimum SDK 26 (Android 8), target SDK — совместимый с выбранным AGP и актуальными требованиями Android. Для локального Android TV 12 прототипа допустимо явно тестировать `targetSdk 32`; перед выпуском обновить target и обработку ограничений foreground service.

> Важное замечание: код ниже — **референсная реализация MVP**, а не заранее собранный/проверенный APK. Потребуются стандартные Android imports, generated resources и адаптация к версии SDK. После каждой стадии собирать и тестировать на реальном TCL.

## 5. Структура проекта

```text
mvp-session-overlay/
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradle/wrapper/...
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/pt/mvplisboa/sessionoverlay/
        │   ├── SetupActivity.kt
        │   ├── OverlayService.kt
        │   ├── OverlayWindow.kt
        │   ├── ApiServer.kt
        │   └── BootReceiver.kt
        └── res/
            ├── drawable-nodpi/session_ended.png
            ├── mipmap-*/ic_launcher.png
            └── values/strings.xml
```

## 6. AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <application
        android:label="MVP Session Overlay"
        android:theme="@android:style/Theme.Material.NoActionBar"
        android:usesCleartextTraffic="true"
        android:allowBackup="false">
        <activity android:name=".SetupActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
        <service android:name=".OverlayService" android:exported="false" />
        <receiver android:name=".BootReceiver" android:enabled="true" android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

`usesCleartextTraffic` включён только для внутреннего HTTP; в производственной версии лучше HTTPS через локальный reverse proxy/VPN, если есть риск недоверенных клиентов LAN. Для target SDK 34+ требуется объявление подходящего **foreground service type** и соответствующих разрешений; выбирать тип только по фактической задаче и требованиям Android, не обходить ограничения неверным типом.

## 7. OverlayWindow.kt — ключевой proof-of-concept

```kotlin
package pt.mvplisboa.sessionoverlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

class OverlayWindow(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: ImageView? = null

    fun show() {
        if (view != null) return
        check(Settings.canDrawOverlays(context)) { "Overlay permission not granted" }
        val image = ImageView(context).apply {
            setImageResource(R.drawable.session_ended)
            scaleType = ImageView.ScaleType.FIT_XY // заполняет экран; для сохранения пропорций использовать CENTER_CROP
            setBackgroundColor(android.graphics.Color.BLACK)
            systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(image, params)
        view = image
    }

    fun hide() {
        view?.let { wm.removeViewImmediate(it) }
        view = null
    }

    fun isVisible(): Boolean = view != null
}
```

`FIT_XY` растягивает картинку до соотношения экрана; предпочтительно подготовить исходник точно под физическое разрешение ТВ (`adb shell wm size`). Для разных экранов — `CENTER_CROP` (возможна обрезка текста; заложить безопасные поля).

**Критично:** все вызовы `WindowManager.addView/removeView` выполняются на главном Android-потоке. HTTP-сервер вызывает `Handler(Looper.getMainLooper()).post { ... }`.

## 8. SetupActivity.kt — выдача разрешения и запуск сервиса

```kotlin
package pt.mvplisboa.sessionoverlay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout

class SetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun button(label: String, action: () -> Unit) {
            layout.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        button("Grant overlay permission") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        button("Start service") {
            if (Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, OverlayService::class.java))
            }
        }
        button("Show test") { startService(Intent(this, OverlayService::class.java).setAction("SHOW")) }
        button("Hide test") { startService(Intent(this, OverlayService::class.java).setAction("HIDE")) }
        setContentView(layout)
    }
}
```

На Android TV экран выдачи overlay permission может отличаться или быть недоступным; тогда проверить в **Settings → Apps → Special app access → Display over other apps**. Для отладки допускается `adb shell appops set pt.mvplisboa.sessionoverlay SYSTEM_ALERT_WINDOW allow`, но наличие AppOps не гарантирует разрешение на всех прошивках TCL.

## 9. OverlayService.kt — жизненный цикл и foreground notification

Сервис должен создать канал уведомлений, вызвать `startForeground(...)` **сразу после старта**, затем запустить HTTP-сервер. Ниже — опорный код (в Android Studio добавить импорты).

```kotlin
package pt.mvplisboa.sessionoverlay

import android.app.*
import android.content.Intent
import android.os.*

class OverlayService : Service() {
    private lateinit var overlay: OverlayWindow
    private lateinit var api: ApiServer
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayWindow(this)
        val channel = NotificationChannel("mvp_service", "MVP Overlay Service",
            NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = Notification.Builder(this, "mvp_service")
            .setContentTitle("MVP Session Overlay")
            .setContentText("LAN control enabled")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
        startForeground(1001, notification)
        api = ApiServer(5002) { command, callback ->
            main.post {
                val result = runCatching {
                    when (command) {
                        "show" -> overlay.show()
                        "hide" -> overlay.hide()
                    }
                    overlay.isVisible()
                }
                callback(result)
            }
        }
        api.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SHOW" -> main.post { runCatching { overlay.show() } }
            "HIDE" -> main.post { overlay.hide() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        api.close()
        overlay.hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
```

**Уточнение:** `ApiServer` в этом фрагменте — проектный интерфейс; его необходимо реализовать (раздел 10). `callback` вызывается после завершения UI-операции, чтобы HTTP-ответ отражал фактическое состояние. Не запускать тяжёлый код на main thread.

## 10. API-сервер: контракт и требования к реализации

Для MVP подойдёт NanoHTTPD (добавить совместимую зависимость Gradle) либо собственный `ServerSocket`. **Рекомендуется NanoHTTPD** для минимизации ошибок HTTP-парсинга. Конкретную версию зависимости закрепить после проверки совместимости с проектом. Ниже — **контракт**, а не готовая реализация NanoHTTPD.

### Протокол

```http
POST /show HTTP/1.1
Host: 192.168.10.53:5002
X-API-Key: REPLACE_WITH_LONG_RANDOM_TOKEN
Content-Type: application/json

{}
```

Ответ:

```json
{"ok":true,"visible":true}
```

`POST /hide` → `{"ok":true,"visible":false}`; `GET /status` → `{"ok":true,"visible":false,"version":"1.0.0","service_running":true}`.

Правила реализации:

1. Проверять `X-API-Key` **до** обработки команды, включая `/status` (для `/health` можно разрешить только localhost).
2. На неверный ключ отвечать `401`.
3. Только `GET /status`, `GET /health`, `POST /show`, `POST /hide`; остальные — `404/405`.
4. Не доверять переданному `visible`: состояние возвращает `OverlayWindow`.
5. Выполнять операции через главный поток и ждать результата с таймаутом 2 секунды; при ошибке возвращать `500` с диагностикой.
6. Ограничить размер запроса (например, 4 КБ), число соединений и время ожидания.
7. Включить логирование: timestamp, команда, результат, IP отправителя; не логировать секретный токен.
8. Слушать `0.0.0.0:5002` только в доверенной VLAN; при возможности ограничить входящие подключения firewall/роутером.
9. Для настоящего ggLeap-интегратора использовать очередь и повторные попытки с backoff, но не повторять `show` для уже продлённой сессии.

### Тесты API с Mac

```bash
export TV=192.168.10.53
export MVP_OVERLAY_TOKEN='REPLACE_WITH_LONG_RANDOM_TOKEN'

curl -i --max-time 5 -H "X-API-Key: $MVP_OVERLAY_TOKEN" "http://$TV:5002/status"
curl -i --max-time 5 -X POST -H "X-API-Key: $MVP_OVERLAY_TOKEN" "http://$TV:5002/show"
curl -i --max-time 5 -X POST -H "X-API-Key: $MVP_OVERLAY_TOKEN" "http://$TV:5002/hide"
```

Для **самого первого proof-of-concept** допустимо тестировать `SHOW`/`HIDE` через `adb shell am startservice` после ручного запуска foreground service из Activity, ещё до реализации HTTP API. Это позволяет быстро проверить главный риск — перекрытие HDMI.

## 11. BootReceiver.kt и поведение после перезапуска

```kotlin
package pt.mvplisboa.sessionoverlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Android 12+ ограничивает запуск foreground services из фона.
        // Для данного устройства проверить допустимость boot-start.
        // НЕ включать overlay автоматически: состояние должен подтвердить сервер клуба.
        if (Build.VERSION.SDK_INT < 31) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
    }
}
```

На Android 12+ boot receiver **может** иметь разрешённые исключения для запуска foreground service, но это зависит от обстоятельств и target SDK. Для production реализовать и протестировать конкретный разрешённый сценарий; если система блокирует запуск — показывать доступный пользователю механизм ручного запуска и использовать управление устройством (MDM/device owner) при необходимости. Не заявлять гарантированный автозапуск без проверки на TCL.

## 12. Подготовка PNG

1. Возьми уже созданный `image.png` (TIME IS UP, MVP Lisboa).
2. Проверь реальное разрешение ТВ: `adb shell wm size`.
3. Экспортируй под экран (например, 1920×1080, если устройство сообщает FHD), сохрани читаемые отступы.
4. Положи файл в `app/src/main/res/drawable-nodpi/session_ended.png`.
5. Убедись, что фон изображения полностью непрозрачен (RGB или PNG без альфа-прозрачности).
6. Для проверки на Mac: `sips -g pixelWidth -g pixelHeight image.png`.

**Рекомендация:** текст и графика должны находиться внутри центральной безопасной области (~90% ширины/высоты), чтобы избежать обрезки/overscan на телевизоре.

## 13. Сборка APK

В Android Studio: **Build → Build APK(s)**. Либо из корня проекта:

```bash
chmod +x ./gradlew
./gradlew :app:assembleDebug
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

Ожидаемый файл: `app/build/outputs/apk/debug/app-debug.apk`. Для production: создать keystore, настроить release signing, `./gradlew :app:assembleRelease`, безопасно сохранить ключ и его пароль. Не публиковать API-токен в репозитории и не хранить его прямо в исходниках.

## 14. Установка APK на TCL через ADB

```bash
adb connect 192.168.10.53:5555
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm list packages | grep mvplisboa
adb shell am start -n pt.mvplisboa.sessionoverlay/.SetupActivity
```

На ТВ выдать разрешение «Показывать поверх других приложений» и нажать **Start service**.

Если системный экран разрешения не открывается:

```bash
adb shell appops get pt.mvplisboa.sessionoverlay SYSTEM_ALERT_WINDOW
adb shell appops set pt.mvplisboa.sessionoverlay SYSTEM_ALERT_WINDOW allow
adb shell appops get pt.mvplisboa.sessionoverlay SYSTEM_ALERT_WINDOW
```

Это **диагностический способ**, не универсальная замена системному разрешению.

Проверить процесс и порт:

```bash
adb shell pidof pt.mvplisboa.sessionoverlay
adb shell dumpsys activity services pt.mvplisboa.sessionoverlay
curl -i --max-time 5 http://192.168.10.53:5002/health
```

Если `/health` защищён ключом — добавить заголовок `X-API-Key`.

## 15. Главный тест: перекрытие PS5 на HDMI 4

**Порядок:**

1. Включить PS5 и вывести движущуюся картинку игры на HDMI 4.
2. Убедиться, что TvOverlay продолжает показывать время (если тестируется одновременно).
3. Запустить новый foreground service.
4. Отправить `POST /show`.
5. Проверить: изображение заполняет **весь экран**, включая углы, нет полупрозрачности, PS5 не переключилась на Home.
6. Подождать 5 минут, затем 30 минут; проверить стабильность.
7. Отправить `POST /hide`; проверить немедленное восстановление картинки PS5.
8. Повторить 20 циклов show/hide, включая повторные show/show и hide/hide.
9. Переключить входы, выключить/включить экран и проверить восстановление.
10. Перезагрузить ТВ и проверить сервис, права, API, отсутствие ложной блокировки.

**Если PNG отображается только поверх Android Home, но не поверх HDMI:** это ограничение композитора/прошивки TCL, а не ошибка HTTP. Не пытаться «лечить» сменой размеров изображения. Проверить, что окно `TYPE_APPLICATION_OVERLAY` действительно добавлено (`adb logcat`, `dumpsys window`), и рассмотреть альтернативы: Android TV input framework/OEM API, внешний HDMI overlay/scaler или управление HDMI через промежуточное устройство. Стандартный APK не гарантирует доступ к аппаратному HDMI-видеослою.

## 16. Тестирование отказов и диагностика

### Сбор логов

```bash
adb logcat -c
adb logcat -v time | grep -E 'MVPOverlay|AndroidRuntime|WindowManager|ForegroundService'
adb shell dumpsys window windows | grep -i -A 12 mvplisboa
adb shell dumpsys package pt.mvplisboa.sessionoverlay | grep -A 15 'requested permissions'
```

### Симптомы

| Симптом | Возможная причина | Действие |
|---|---|---|
| `curl: (7) Couldn't connect` | Сервис не запущен, порт не слушает, firewall | Проверить PID, логи, Wi-Fi/LAN |
| HTTP `401` | Неверный токен | Проверить заголовок |
| HTTP `200`, нет картинки | Разрешение, ошибка UI-потока, HDMI compositor | `Settings.canDrawOverlays`, logcat, тест на Android Home и HDMI |
| `BadTokenException` | Не выдано разрешение или неверный тип окна | Проверить AppOps/manifest/SDK |
| Изображение обрезано | Aspect ratio/overscan | Подготовить PNG под `wm size`, `CENTER_CROP`/`FIT_XY` |
| Сервис исчез после `force-stop` | Android блокирует автоматический рестарт force-stopped приложения | Запустить `SetupActivity` вручную |
| После reboot API не поднялся | Boot restriction / энергосбережение | Проверить логи, ручной запуск, режим управления устройством |

### Аварийное снятие блокировки

```bash
# Предпочтительно — API
curl -X POST -H "X-API-Key: $MVP_OVERLAY_TOKEN" http://192.168.10.53:5002/hide

# Если API не отвечает — остановить только НАШЕ приложение:
adb shell am force-stop pt.mvplisboa.sessionoverlay

# Затем открыть настройки/запустить приложение:
adb shell am start -n pt.mvplisboa.sessionoverlay/.SetupActivity
```

**Не останавливать `com.tabdeveloper.tvoverlay`**, если нужно сохранить действующий таймер. Не использовать `pm clear`, если не требуется сброс настроек.

## 17. Интеграция с Python / ggLeap

Python-контроллер должен получать события начала, продления и окончания сеанса из вашей внутренней системы/ggLeap. **Не считать один webhook абсолютной истиной**: события могут приходить повторно, с задержкой или не по порядку.

Пример клиента (после реализации API):

```python
import os
import requests

TV = os.getenv("MVP_TV_IP", "192.168.10.53")
TOKEN = os.environ["MVP_OVERLAY_TOKEN"]

session = requests.Session()
session.headers.update({"X-API-Key": TOKEN})

def overlay_show():
    r = session.post(f"http://{TV}:5002/show", json={}, timeout=3)
    r.raise_for_status()
    return r.json()

def overlay_hide():
    r = session.post(f"http://{TV}:5002/hide", json={}, timeout=3)
    r.raise_for_status()
    return r.json()
```

### Рекомендуемая логика состояния

```text
IDLE → ACTIVE → EXPIRED → ACTIVE (продление)
  ↑       |        |
  └───────┴────────┘ (завершение / освобождение места)
```

- `ACTIVE`: TvOverlay обновляет обратный отсчёт; экран окончания скрыт.
- `EXPIRED`: контроллер вызывает `/show`.
- `EXTENDED`: сервер подтверждает новое `expires_at`; контроллер вызывает `/hide` и обновляет таймер.
- `IDLE`: скрыть оверлей, убрать/сбросить таймер.
- Время считать по серверному UTC timestamp; на клиенте отображать остаток, не уменьшать «локальный счётчик» как единственный источник истины.
- После reconnect выполнять reconciliation: сравнить фактический `/status` с текущим состоянием сессии и привести ТВ к нужному виду.
- Вести журнал: TV, session_id, action, timestamp, API response, retry_count.

## 18. Тест-матрица перед внедрением

| № | Сценарий | Ожидаемый результат |
|---|---|---|
| T01 | HDMI 4 + `/show` | Полный непрозрачный экран |
| T02 | `/hide` | HDMI виден, PS5 не прерывалась |
| T03 | 20× `/show` | Одно окно, без утечек |
| T04 | 20× `/hide` | Ошибок нет |
| T05 | Нет разрешения overlay | Корректная ошибка, инструкции |
| T06 | Неверный API-токен | `401`, состояние не изменено |
| T07 | Сеть отключена на 60 с | Состояние окна сохраняется; восстановление API после сети |
| T08 | Сервис упал | Лог, возможность перезапуска |
| T09 | Reboot TCL | Сервис восстанавливается или администратор получает понятный путь запуска |
| T10 | 30 минут EXPIRED | Оверлей остаётся видимым |
| T11 | Продление ggLeap | Скрытие экрана и новый таймер |
| T12 | Дублированный webhook | Без мигания и повторного окна |
| T13 | TvOverlay и новый APK одновременно | Оба не конфликтуют |
| T14 | Android Home → HDMI 4 | Оверлей ведёт себя предсказуемо |
| T15 | `force-stop` нового APK | Изображение исчезает, TvOverlay не затронут |

## 19. Порядок разработки — минимизация риска

**Этап A (сначала):** проект Android Studio, `SetupActivity`, `OverlayWindow`, кнопки `Show test`/`Hide test`, разрешение overlay. Собрать APK, установить, проверить **именно HDMI 4**. Если не работает — не тратить время на API и интеграцию.

**Этап B:** добавить foreground service и REST API на `5002`, авторизацию, `/status`, логирование.

**Этап C:** надёжность — boot, reconnect, контроль состояния, обработка ошибок, длительный тест.

**Этап D:** подключить Python-контроллер к событиям ggLeap и обновлению TvOverlay; протестировать продление и окончание сессии.

## 20. Definition of Done

Работа считается завершённой только если: (1) APK действительно собран; (2) установлен на TCL; (3) разрешение выдано; (4) `POST /show` закрывает **весь HDMI 4**; (5) `POST /hide` восстанавливает PS5; (6) API защищён; (7) 30-минутный тест и reboot пройдены; (8) интеграция ggLeap прошла проверку на реальном игровом сеансе.

**Не путать документацию с готовым APK:** этот документ задаёт полный план и основные кодовые заготовки, но не является уже собранным Android-проектом. Рекомендуется начать с этапа A, прежде чем реализовывать остальные компоненты.
