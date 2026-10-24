import SwiftUI
import UIKit
import ComposeApp

/// Hosts the shared Compose Multiplatform UI (`MainViewController.kt` in
/// `composeApp/src/iosMain`) inside a SwiftUI app shell.
struct ComposeRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct UniqueLoApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRoot()
                // The shared shell applies safe-area insets itself
                // (App.kt: WindowInsets.safeDrawing) and handles the keyboard
                // through Compose. SwiftUI must not add a second inset, or the
                // Compose surface is letterboxed inside the device safe area.
                .ignoresSafeArea()
        }
    }
}
