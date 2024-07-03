<a name="readme-top"></a>
<!--
*** Thanks for checking out the Best-README-Template. If you have a suggestion
*** that would make this better, please fork the repo and create a pull request
*** or simply open an issue with the tag "enhancement".
*** Don't forget to give the project a star!
*** Thanks again! Now go create something AMAZING! :D
-->



<!-- PROJECT SHIELDS -->
<!--
*** I'm using markdown "reference style" links for readability.
*** Reference links are enclosed in brackets [ ] instead of parentheses ( ).
*** See the bottom of this document for the declaration of the reference variables
*** for contributors-url, forks-url, etc. This is an optional, concise syntax you may use.
*** https://www.markdownguide.org/basic-syntax/#reference-style-links
-->
[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![Apache 2.0 License][license-shield]][license-url]



<!-- PROJECT LOGO -->
<br />
<div align="center">
  <a href="https://github.com/capcom6/android-sms-gateway">
    <img src="assets/logo.png" alt="Logo" width="80" height="80">
  </a>

<h3 align="center">Android SMS Gateway</h3>

  <p align="center">
    Turns your smartphone into an SMS gateway for sending messages via API.
    <br />
    <a href="https://sms.capcom.me"><strong>Explore the docs »</strong></a>
    <br />
    <br />
    <!-- <a href="https://github.com/capcom6/android-sms-gateway">View Demo</a> -->
    <a href="https://github.com/capcom6/android-sms-gateway/issues">Report Bug</a>
    ·
    <a href="https://github.com/capcom6/android-sms-gateway/issues">Request Feature</a>
  </p>
</div>



<!-- TABLE OF CONTENTS -->
- [About The Project](#about-the-project)
  - [Features](#features)
  - [Ideal For](#ideal-for)
  - [Built With](#built-with)
- [Installation](#installation)
  - [Prerequisites](#prerequisites)
    - [Permissions](#permissions)
  - [Installation from APK](#installation-from-apk)
- [Getting Started](#getting-started)
  - [Local server](#local-server)
  - [Cloud server](#cloud-server)
  - [Webhooks](#webhooks)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)
- [Contact](#contact)

<!-- ABOUT THE PROJECT -->
## About The Project

<p align="center"><img src="assets/screenshot.png" width="360"></p>


Android SMS Gateway turns your Android smartphone into an SMS gateway. It's a lightweight application that allows you to send SMS messages programmatically via an API and receive webhooks on incoming SMS. This makes it ideal for integrating SMS functionality into your own applications or services.

### Features

- **No registration required:** No registration or email is required to create an account. In local mode, you don't need an account at all!
- **Send SMS via API:** Use our API to send messages directly from your applications or services.
- **Support for Android 5.0 and above:** The application is compatible with Android 5.0 and later versions.
- **Message status tracking:** Monitor the status of sent messages in real-time.
- **Automatic startup:** The application starts running as soon as your device boots up.
- **Support for multiple SIM cards:** The application supports devices with multiple SIM cards.
- **Multipart messages:** The application supports sending long messages with auto-partitioning.
- **End-to-end encryption:** The application provides end-to-end encryption by encrypting message content and recipients' phone numbers before sending them to the API and decrypting them on the device.
- **Message expiration:** The application allows setting an expiration time for messages. Messages will not be sent if they have expired.
- **Random delay between messages:** Introduces a random delay between sending messages to avoid mobile operator restrictions.
- **Private server support:** The application allows for the use of a backend server in the user's infrastructure for enhanced security.
- **App status reporting:** Ability to report current app status by sending requests to specified URL at any user-defined intervals.
- **Webhooks on incoming SMS:** The application allows setting up webhooks to be sent to a specified URL whenever an SMS is received.

### Ideal For

- Notifications
- Alerts
- Two-factor authentication codes
- Receiving incoming SMS

Android SMS Gateway offers a convenient and reliable solution for sending notifications, alerts, or two-factor authentication codes, and also allows you to receive webhooks when an SMS is received.

*Note*: It is not recommended to use this for batch sending due to potential mobile operator restrictions.

<p align="right">(<a href="#readme-top">back to top</a>)</p>



### Built With

- [![Kotlin](https://img.shields.io/badge/Kotlin-000000?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
- [![Ktor](https://img.shields.io/badge/Ktor-000000?style=for-the-badge&logoColor=white)](https://ktor.io/)
- [![Room](https://img.shields.io/badge/Room-000000?style=for-the-badge&logoColor=white)](https://developer.android.com/training/data-storage/room)
- [![Firebase](https://img.shields.io/badge/Firebase-000000?style=for-the-badge&logo=firebase&logoColor=white)](https://firebase.google.com/)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Installation

You can install app to your device from prebuilt APK or by building from sources.

### Prerequisites

You need an Android device with Android 5.0 (Lollipop) or above for using the application.

#### Permissions

To use the application, you need to grant the following permissions:

- **SEND_SMS**: This permission is required to send SMS messages.
- **READ_PHONE_STATE**: This permission is optional. If you want to select the SIM card, you can grant this permission.
- **RECEIVE_SMS**: This permission is optional. If you want to receive webhooks on incoming SMS, you need to grant this permission.

### Installation from APK

1. Navigate to the [Releases](https://github.com/capcom6/android-sms-gateway/releases) page.
2. Download the latest APK file from the list of available releases.
3. Transfer the APK file to your Android device.
4. On your Android device, go to **Settings** > **Security** (or **Privacy** on some devices).
5. Enable the **Unknown sources** option to allow installation of apps from sources other than the Play Store.
6. Use a file manager app to navigate to the location of the downloaded APK file.
7. Tap on the APK file to start the installation process.
8. Follow the on-screen prompts to complete the installation.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- GETTING STARTED -->
## Getting Started

_For integration examples, please refer to the [API Documentation](https://sms.capcom.me/integration/api/)_

The Android SMS Gateway can work in two modes: with a local server started on the device or with a cloud server at [sms.capcom.me](https://sms.capcom.me). The basic API is the same for both modes and is documented on the [Android SMS Gateway API Documentation](https://capcom6.github.io/android-sms-gateway/).

### Local server

<div align="center">
    <img src="/assets/local-server.png" alt="Local server example settings">
</div>

This mode is ideal for sending messages from a local network.

1. Launch the app on your device.
2. Toggle the `Local Server` switch to the "on" position.
3. Tap the `Offline` button located at the bottom of the screen to activate the server.
4. The `Local Server` section will display your device's local and public IP addresses, as well as the credentials for basic authentication. Please note that the public IP address is only accessible if you have a public (also known as "white") IP and your firewall is configured appropriately.
    <div align="center">
        <img src="/assets/local-server.png" alt="Example settings for Local Server mode">
    </div>
5. To send a message from within the local network, execute a `curl` command like the following. Be sure to replace `<username>`, `<password>`, and `<device_local_ip>` with the actual values provided in the previous step:

    ```sh
    curl -X POST -u <username>:<password> -H "Content-Type: application/json" -d '{ "message": "Hello, world!", "phoneNumbers": ["+79990001234", "+79995556677"] }' http://<device_local_ip>:8080/message
    ```

### Cloud server

<div align="center">
    <img src="/assets/cloud-server.png" alt="Cloud server example settings">
</div>

Use the cloud server mode when dealing with dynamic or shared device IP addresses. The best part? No registration, email, or phone number is required to start using it.

1. Launch the app on your device.
2. Toggle the `Cloud Server` switch to the "on" position.
3. Tap the `Online` button located at the bottom of the screen to connect to the cloud server.
4. In the `Cloud Server` section, the credentials for basic authentication will be displayed.
   <div align="center">
      <img src="/assets/cloud-server.png" alt="Example settings for Cloud Server mode">
   </div>
5. To send a message via the cloud server, perform a `curl` request with a command similar to the following, substituting `<username>` and `<password>` with the actual values obtained in step 4:

    ```sh
    curl -X POST -u <username>:<password> -H "Content-Type: application/json" -d '{ "message": "Hello, world!", "phoneNumbers": ["+79990001234", "+79995556677"] }' https://sms.capcom.me/api/3rdparty/v1/message
    ```

For further privacy, you can deploy your own private server. See the [Private Server](https://sms.capcom.me/getting-started/private-server/) section for more details.

### Webhooks

Webhooks can be utilized to get notifications of incoming SMS messages.

Follow these steps to set up webhooks:

1. Set up your own HTTP server with a valid SSL certificate to receive webhooks. For testing purposes, [webhook.site](https://webhook.site) can be useful.
2. Register your webhook with an API request:

    ```sh
    curl -X POST -u <username>:<password> \
      -H "Content-Type: application/json" \
      -d '{ "id": "unique-id", "url": "https://webhook.site/<your-uuid>", "event": "sms:received" }' \
      http://<device_local_ip>:8080/webhooks
    ```
    
3. Send an SMS to the device.
4. The application will dispatch POST request to the specified URL with a payload such as:

    ```json
    {
      "event": "sms:received",
      "payload": {
        "message": "Received SMS text",
        "phoneNumber": "+79990001234",
        "receivedAt": "2024-06-07T11:41:31.000+07:00"
      }
    }
    ```

5. To deregister a webhook, execute a `curl` request using the following pattern:

    ```sh
    curl -X DELETE -u <username>:<password> \
      http://<device_local_ip>:8080/webhooks/unique-id
    ```

For cloud mode the process is similar, simply change the URL to https://sms.capcom.me/api/3rdparty/v1/webhooks. Webhooks in Local and Cloud mode are independent.

*Note*: Webhooks are transmitted directly from the device; therefore, the device must have an outgoing internet connection. As the requests originate from the device, incoming messages remain inaccessible to us.


<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- ROADMAP -->
## Roadmap

- [ ] Add functionality to modify user credentials.
- [ ] Introduce option to adjust the local server port.
- [ ] Send notifications to an external server when the status of a message changes.
- [ ] Incorporate scheduling capabilities for dispatching messages at specific times.
- [ ] Implement region-based restrictions to prevent international SMS.
- [ ] Provide an API endpoint to retrieve the list of available SIM cards on the device.
- [x] Include detailed error messages in responses and logs.

See the [open issues](https://github.com/capcom6/android-sms-gateway/issues) for a full list of proposed features (and known issues).

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- CONTRIBUTING -->
## Contributing

Contributions are what make the open source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

If you have a suggestion that would make this better, please fork the repo and create a pull request. You can also simply open an issue with the tag "enhancement".
Don't forget to give the project a star! Thanks again!

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- LICENSE -->
## License

Distributed under the Apache-2.0 license. See [LICENSE](LICENSE) for more information.

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- CONTACT -->
## Contact

Project Link: [https://github.com/capcom6/android-sms-gateway](https://github.com/capcom6/android-sms-gateway)

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- ACKNOWLEDGMENTS -->
<!-- ## Acknowledgments

Use this space to list resources you find helpful and would like to give credit to. I've included a few of my favorites to kick things off!

* [Choose an Open Source License](https://choosealicense.com)
* [GitHub Emoji Cheat Sheet](https://www.webpagefx.com/tools/emoji-cheat-sheet)
* [Malven's Flexbox Cheatsheet](https://flexbox.malven.co/)
* [Malven's Grid Cheatsheet](https://grid.malven.co/)
* [Img Shields](https://shields.io)
* [GitHub Pages](https://pages.github.com)
* [Font Awesome](https://fontawesome.com)
* [React Icons](https://react-icons.github.io/react-icons/search)

<p align="right">(<a href="#readme-top">back to top</a>)</p> -->



<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->
[contributors-shield]: https://img.shields.io/github/contributors/capcom6/android-sms-gateway.svg?style=for-the-badge
[contributors-url]: https://github.com/capcom6/android-sms-gateway/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/capcom6/android-sms-gateway.svg?style=for-the-badge
[forks-url]: https://github.com/capcom6/android-sms-gateway/network/members
[stars-shield]: https://img.shields.io/github/stars/capcom6/android-sms-gateway.svg?style=for-the-badge
[stars-url]: https://github.com/capcom6/android-sms-gateway/stargazers
[issues-shield]: https://img.shields.io/github/issues/capcom6/android-sms-gateway.svg?style=for-the-badge
[issues-url]: https://github.com/capcom6/android-sms-gateway/issues
[license-shield]: https://img.shields.io/github/license/capcom6/android-sms-gateway.svg?style=for-the-badge
[license-url]: https://github.com/capcom6/android-sms-gateway/blob/master/LICENSE


