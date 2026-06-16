# 📱 Эхофон — Android-приложение

Клиентская часть сервиса [Эхофон](https://echophone.ru) — пересылка SMS и push-уведомлений с Android-телефона в браузер.

## 🔒 Безопасность

Все уведомления шифруются **на устройстве** алгоритмом **AES-256-GCM** до отправки на сервер. Сервер получает только зашифрованный набор байтов и не может прочитать содержимое.

Ключ шифрования генерируется из пароля пользователя через **PBKDF2** (10 000 итераций, SHA-256). Пароль никогда не передаётся на сервер — только его bcrypt-хеш.

[Подробнее о безопасности](https://echophone.ru/blog/security-implementation)

## 🛠 Технологии

- **Язык:** Java
- **HTTP-клиент:** Retrofit 2 + OkHttp
- **Шифрование:** AES-256-GCM (Java Cryptography Architecture)
- **Перехват SMS:** ContentObserver
- **Перехват уведомлений:** NotificationListenerService
- **Фоновая работа:** Foreground Service

## 📦 Установка

Скачайте актуальный APK с [официального сайта](https://echophone.ru/downloads/echophone.apk?utm_source=github&utm_medium=readme&utm_campaign=oss).


app/src/main/java/com/example/gafhubforwarder/
├── api/
│   ├── ApiClient.java          # Retrofit-клиент
│   └── ApiService.java         # API-эндпоинты
├── models/                     # Модели запросов/ответов
├── services/
│   ├── MessageSenderService.java  # Отправка с retry-логикой
│   ├── NotificationListener.java # Перехват уведомлений
│   └── SmsReceiver.java          # Перехват SMS
├── ui/                         # Activity (логин, настройки)
└── utils/
    ├── EncryptionManager.java  # Шифрование AES-256-GCM
    ├── DeviceInfo.java         # Идентификация устройства
    └── PreferencesManager.java # Хранение API-ключа


Как работает шифрование
Пользователь вводит пароль при логине

EncryptionManager.generateKey(password, salt) → AES-256 ключ

При получении уведомления → EncryptionManager.encrypt(plainText, key) → Base64

Зашифрованное сообщение отправляется на сервер

Сервер сохраняет как есть, не расшифровывая

Браузер расшифровывает через Web Crypto API
