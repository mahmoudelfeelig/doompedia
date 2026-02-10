import SwiftUI

@main
struct DoompediaApp: App {
    @StateObject private var viewModel = MainViewModel.make()

    var body: some Scene {
        WindowGroup {
            RootView(viewModel: viewModel)
                .preferredColorScheme(viewModel.colorScheme)
                .tint(appAccentColor)
        }
    }

    private var appAccentColor: Color {
        Color.fromHexForApp(viewModel.settings.accentHex) ?? .green
    }
}

private extension Color {
    static func fromHexForApp(_ hex: String) -> Color? {
        let cleaned = hex
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")
        guard cleaned.count == 6, let value = UInt64(cleaned, radix: 16) else {
            return nil
        }
        return Color(
            .sRGB,
            red: Double((value & 0xFF0000) >> 16) / 255.0,
            green: Double((value & 0x00FF00) >> 8) / 255.0,
            blue: Double(value & 0x0000FF) / 255.0,
            opacity: 1.0
        )
    }
}
