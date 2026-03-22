# dvait
> A private and seamless digital extension of your memory.

`dvait` is a sophisticated Android application designed to provide users with a "digital memory" by capturing, indexing, and querying their device's visual and notification history. It prioritizes privacy by processing all sensitive data on-device and using local semantic search.




https://github.com/user-attachments/assets/2b65018d-34d9-4d2b-8ce3-f9b29e00bc6a




## 🏗 Architecture
The project follows a modern Android Architecture with a focus on high-performance background processing and reactive UI.

- **UI Layer**: Built entirely with **Jetpack Compose**. Uses dynamic theming based on system accent colors and a structured onboarding flow.
- **Service Layer**:
    - `ScreenCaptureService`: An `AccessibilityService` that monitors screen changes and extracts text nodes.
    - `NotificationCaptureService`: A `NotificationListenerService` that captures incoming notifications.
    - `ServiceWatchdogWorker`: A `WorkManager` periodic task that ensures background services remain healthy.
- **Data Layer**:
    - **ObjectBox**: Used for high-speed local storage.
    - **DataStore**: Manages user preferences and AI configuration.
- **Core Engine**:
    - `EmbeddingEngine`: Leverages **TensorFlow Lite** with a quantized **MiniLM** model for on-device vector generation.
    - `QueryEngine`: Orchestrates vector search and LLM prompting.
    - `GroqEngine`: Handles stateless API communication with Groq for high-speed inference.

## 🚀 Key Components

### Local Semantic Search
Instead of keyword matching, `dvait` uses vector embeddings. When a user queries their history, the query is vectorized and compared against the local ObjectBox database using cosine similarity.

### Privacy-First AI
While the app supports **Groq** for complex reasoning, the "context" sent to the LLM is strictly limited to the most relevant snippets found in the local vector search. No bulk data or raw screen captures ever leave the device.

## 🛠 Tech Stack
- **Language**: Kotlin 2.x
- **UI**: Jetpack Compose + MD3
- **Database**: ObjectBox (with Vector Support)
- **ML**: TensorFlow Lite (org.tensorflow:tensorflow-lite)
- **Networking**: OkHttp/Retrofit for LLM API interaction
- **Background**: WorkManager + Accessibility API

## ⚖️ License
This project is licensed under the **Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)**. 
- **Personal use is encouraged.**
- **Commercial use is strictly prohibited without explicit written permission.**

---
*Developed for the next generation of personal computing.*
