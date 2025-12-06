# Foodish Rider App

This application is for delivery riders to manage their availability, view new jobs, and handle active orders within the Foodish ecosystem.

> **For a full overview of the entire 3-app ecosystem (including the User and Partner apps), please see the [main project repository](https://github.com/BAPPI-SWE/FOODISH).**
> *(Note: Remember to replace the link above with the link to your new overview repository!)*

## 🚀 Key Features
* **Authentication & Profile:** Secure sign-up and profile creation, including selecting serviceable delivery locations from a dynamic list fetched from Firestore.
* **Real-Time Availability:** A prominent toggle switch allows riders to set their status to "Online" or "Offline," which instantly affects the User App.
* **Dynamic Order Pool:**
    * The main dashboard listens for new, "Pending" orders that match the rider's serviceable locations.
    * Order cards display complete details: restaurant, customer address, phone number, item list, total price, and order time.
* **Order Lifecycle Management:**
    * Riders can **accept** an order, which removes it from the pool for other riders.
    * An **"Active Delivery"** screen appears after acceptance, showing pickup and drop-off details.
    * Riders can update the order status to **"On the way"** (Picked Up) and **"Delivered"**.
* **Account Management:** A dedicated account screen with options to edit the rider's profile and sign out.

## 🛠️ Tech Stack
* **Language:** Kotlin
* **UI:** Jetpack Compose
* **Architecture:** MVVM
* **Navigation:** Jetpack Navigation Compose
* **Backend:** Firebase (Authentication, Firestore)

## ⚙️ Setup
1.  Ensure you have a Firebase project set up.
2.  Add this app to your Firebase project with the package name `com.yumzy.rider`.
3.  Add your debug SHA-1 key to the Firebase project settings.
4.  Download the `google-services.json` file and place it in the `app/` directory.
5.  Build and run.




