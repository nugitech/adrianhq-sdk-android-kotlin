# Adrian Chat for Android

[![JitPack](https://jitpack.io/v/nugitech/adrianhq-sdk-android-kotlin.svg)](https://jitpack.io/#nugitech/adrianhq-sdk-android-kotlin)

[Adrian](https://adrianhq.co) live chat for Android apps (API 23+), from Kotlin or Java.

The library opens your workspace's chat full screen in your brand colour. Customers get everything they have on your website: forms, action buttons, chat flows, file uploads, ratings, help center search and the AI agent. It also gives you a launcher button with an unread badge, and the unread count to show anywhere else in your app.

## Features

- Full-screen messenger that follows your workspace's widget settings: colours, greeting, conversation starters and business hours. It follows the keyboard and system bars edge to edge.
- `AdrianLauncherButton`: a round chat button with an unread badge, for any layout.
- `Adrian.unreadCount`: replies arrive while the chat is closed, because the chat loads in the background.
- `identify`: attach the signed-in customer's name and email to the conversation.
- Screen tracking: agents see which screen the customer is on, as `app://<package name>/<screen>`.
- `reset` on sign-out, so the next person on the device starts a fresh conversation.
- File attachments through the system picker, and automatic recovery if the web view's renderer process dies.

## Install

Add JitPack to your repositories in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then add the dependency to your app module:

```kotlin
dependencies {
    implementation("com.github.nugitech:adrianhq-sdk-android-kotlin:0.1.0")
}
```

The library declares the `INTERNET` permission and its own messenger activity, so there's nothing to add to your manifest. Its ProGuard/R8 rules are included.

## Usage

Find your workspace id in Adrian under **Settings → Chat widget → Install**. It's the same public id your website snippet uses.

```kotlin
class AcmeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Adrian.configure(this, workspaceId = "ws_your_workspace_id")
    }
}
```

Open the chat from anywhere, or drop the launcher into a layout:

```kotlin
Adrian.present(context)
```

```xml
<com.adrianhq.chat.AdrianLauncherButton
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom|end"
    android:layout_margin="20dp" />
```

### Signed-in customers

```kotlin
// After sign-in
Adrian.identify(name = user.name, email = user.email)

// On sign-out: forget this visitor so the next person starts fresh
Adrian.reset()
```

`identify` labels the conversation. It doesn't prove who the customer is, so verify identity before account-sensitive changes, as you would on your website.

### Screens, unread count and events

```kotlin
class MainActivity : AppCompatActivity() {
    private val listener = object : AdrianListener {
        override fun onUnreadCountChanged(count: Int) {
            helpBadge.text = "$count new"
            helpBadge.isVisible = count > 0
        }

        override fun onMessageReceived(message: AdrianMessagePreview) {
            showBanner("${message.author}: ${message.body}")
        }
    }

    override fun onStart() {
        super.onStart()
        Adrian.addListener(listener)
        Adrian.trackScreen("Home")
    }

    override fun onStop() {
        Adrian.removeListener(listener)
        super.onStop()
    }
}
```

| API | Description |
| --- | --- |
| `Adrian.configure(context, workspaceId, widgetUrl, openOnProactiveMessage)` | Start the chat in the background. Call once, in `Application.onCreate`. |
| `Adrian.present(context)` / `Adrian.dismiss()` | Open or close the messenger. |
| `Adrian.identify(name, email)` | Attach the signed-in customer. |
| `Adrian.trackScreen(name)` | Report the current screen. |
| `Adrian.reset()` | Forget the visitor (sign-out). |
| `Adrian.addListener` / `removeListener` | `onReady`, `onOpened`, `onClosed`, `onUnreadCountChanged`, `onMessageReceived`, `onPrimaryColorChanged`, `onUnavailable`. |
| `Adrian.unreadCount` | Unread replies, `0` while the chat is open. |
| `Adrian.primaryColor` | Your brand colour, as a colour int. |
| `Adrian.latestMessage` | The newest unseen message, or `null`. |
| `Adrian.isAvailable` / `isPresented` | Whether the workspace has the chat switched on, and whether it's showing. |

`configure` options:

- `openOnProactiveMessage = true` opens the chat by itself when your team or a trigger starts a conversation.
- `widgetUrl` is only for self-hosted Adrian.

From Java, the methods are static (`Adrian.present(context)`, `Adrian.identify("Ada Obi", "ada@example.com")`), and the properties are read through `Adrian.INSTANCE`, for example `Adrian.INSTANCE.getUnreadCount()`.

## How it works

The library runs Adrian's hosted chat widget in a `WebView`, behind a small native bridge. New chat features therefore reach your app without a library update. Only the widget's own pages load inside the web view, and the JavaScript bridge only accepts messages from that origin. Every other link opens in the browser. Your app ships with the public workspace id only, and no API keys.

## Example

`example` is a sample app. Run it against your workspace with:

```sh
./gradlew :example:installDebug -PadrianWorkspaceId=ws_your_workspace_id
```

## License

MIT. See [LICENSE](LICENSE).
