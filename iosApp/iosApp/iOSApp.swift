import SwiftUI
import UIKit
import Shared

// The shared module has classes of its own called Scene and the like, so SwiftUI's are named in full.

/// Lipovka on the iPhone: every screen is the shared Compose UI from the `shared` module.
@main
struct iOSApp: SwiftUI.App {
    var body: some SwiftUI.Scene {
        SwiftUI.WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                .preferredColorScheme(.light)
        }
    }
}

struct ComposeView: SwiftUI.UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIKit.UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIKit.UIViewController, context: Self.Context) {}
}
