# Android SMS Gateway

This application allows you to use your Android smartphone as an SMS gateway to send SMS via API.

## Features

- lightweight;
- support of Android 5.0 ang above;
- single API for local and remote server;
- tracking the status of the message (sent, delivered);
- start at boot and protection from going to sleep.

## Available modes

- [x] Local web server
- [ ] Use of sms.capcom.me server in pull and push modes (in progress)
- [ ] Self-hosted server in pull-mode

## How to use

1. Build the project and install the APK on your phone.
2. Launch the application. Use the IP address, login and password from the main screen to access the API.
3. Send messages by connecting to a local web server on the device.

## Roadmap

### UI

- [x] Start and stop service
- [x] Display the current status of the service (started/stopped)
- [x] Display local and public addresses
- [ ] Display and change the api key
- [ ] Change server port
- [x] Enable/disable startup on device boot
- [ ] Select mode (local, server, self-hosted)

### Features

- [x] Save messages to local db
- [x] Get message status
- [ ] Pull mode
- [ ] Push mode
- [ ] Web notifications to an external server

---

# Android SMS-шлюз

Это приложение позволяет использовать Android-смартфон в качестве SMS-шлюза для отправки SMS через API.

## Функции

- не требовательно к ресурсам;
- поддержка Android 5.0 и выше;
- единый API для локального и удаленного сервера;
- отслеживание состояния сообщения (отправлено, доставлено);
- запуск при загрузке и защита от ухода в сон.

## Доступные режимы

- [x] Локальный веб-сервер
- [ ] Использование сервера sms.capcom.me в режимах pull и push (в процессе)
- [ ] Собственные серверы в режиме pull

## Как использовать

1. Соберите проект и установите APK на телефон.
2. Запустите приложение. Используйте IP-адрес, логин и пароль с главного экрана для доступа к API.
3. Отправляйте сообщения, подключившись к локальному веб-серверу на устройстве.

## Дорожная карта

### Пользовательский интерфейс

- [x] Запуск и остановка службы
- [x] Отображение текущего состояния службы (запущен/остановлен)
- [x] Отображение локального и публичного адресов
- [ ] Отображение и изменение ключа доступа
- [ ] Изменение порта сервера
- [x] Включение/отключение запуска при загрузке устройства
- [ ] Выбор режима (локальный, сервер, свой сервер)

### Функции

- [x] Сохранять сообщения в локальную БД
- [x] Получать статус сообщения
- [ ] Pull-режим
- [ ] Push-режим
- [ ] Веб-уведомления на внешний сервер
