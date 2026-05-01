# FoodBridge

FoodBridge is an Android app that connects hotels and food donors with NGOs to reduce food waste and improve last-mile food rescue.

## Overview

FoodBridge supports two role-based experiences:

- Hotels / donors can post surplus food with images, manage donation status, and track pickups.
- NGOs can browse available donations, claim pickups, view history, and follow route-based collection flows.

## Core Features

- Role-based login and onboarding
- Hotel and NGO dashboards
- Post donation with image upload
- Browse available donations
- Search and filter donations
- Donation history for both roles
- Real-time notification center
- NGO claim flow
- Pickup route tracking
- Pickup PIN verification
- Profile management with address and contact details
- Developer contact section in profile

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- MVVM
- Hilt / Dagger
- Kotlin Coroutines
- StateFlow
- Firebase Authentication
- Firebase Firestore
- Firebase Storage
- Firebase Cloud Messaging
- Coil
- Retrofit
- Gson
- Gradle

## Project Structure

```text
app/src/main/java/com/example/foodbridge
├── navigation
├── presentation
├── data
├── domain
├── ui
└── util
```

## Build Requirements

- Android Studio
- Android SDK 35
- Min SDK 26
- JDK 11 compatible Android build setup
- Firebase project configuration with `google-services.json`

## Run Locally

1. Open the project in Android Studio.
2. Sync Gradle.
3. Ensure Firebase configuration is present.
4. Run the app on an emulator or physical device.

## Release APK

The release APK can be generated with:

```bash
./gradlew assembleRelease
```

This workspace also supports generating a signed APK for manual installation.

## Use Case

FoodBridge is designed for:

- Hotels
- Restaurants
- Caterers
- Bakeries
- NGOs
- Food redistribution teams

## Future Scope

- Payment or sponsor support
- Advanced analytics dashboard
- Multi-city NGO routing
- Admin panel
- Better live tracking integration
- In-app chat between donor and NGO

## Author

- Harsh Thakur
- Contact: harshthakur54@gmail.com

