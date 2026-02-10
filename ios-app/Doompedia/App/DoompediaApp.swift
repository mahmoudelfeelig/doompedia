import SwiftUI

@main
struct DoompediaApp: App {
    @StateObject private var viewModel = MainViewModel.make()

    var body: some Scene {
        WindowGroup {
            RootView(viewModel: viewModel)
                .preferredColorScheme(viewModel.colorScheme)
                .tint(appAccentColor)
                .dynamicTypeSize(dynamicTypeSize)
                .environment(\.legibilityWeight, viewModel.settings.highContrast ? .bold : nil)
                .transaction { transaction in
                    if viewModel.settings.reduceMotion {
                        transaction.animation = nil
                    }
                }
        }
    }

    private var appAccentColor: Color {
        Color.fromHexForApp(viewModel.settings.accentHex) ?? .green
    }

    private var dynamicTypeSize: DynamicTypeSize {
        switch viewModel.settings.fontScale {
        case ..<0.9:
            return .small
        case 0.9 ..< 1.0:
            return .medium
        case 1.0 ..< 1.1:
            return .large
        case 1.1 ..< 1.2:
            return .xLarge
        case 1.2 ..< 1.3:
            return .xxLarge
        default:
            return .xxxLarge
        }
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
