import SwiftUI
import UIKit

struct SettingsView: View {
    @ObservedObject var viewModel: MainViewModel

    @State private var accentHexDraft = ""
    @State private var importDraft = ""

    private let presets = [
        "#0B6E5B",
        "#1363DF",
        "#C44536",
        "#F59E0B",
        "#7E22CE",
        "#EC4899",
    ]

    var body: some View {
        NavigationStack {
            Form {
                Section("Mode") {
                    Picker("Feed mode", selection: Binding(
                        get: { viewModel.settings.feedMode },
                        set: { viewModel.setFeedMode($0) }
                    )) {
                        ForEach(FeedMode.allCases, id: \.self) { mode in
                            Text(mode.rawValue).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)

                    Text(viewModel.settings.feedMode == .offline
                         ? "OFFLINE uses downloaded packs and local cache only."
                         : "ONLINE fetches live Wikipedia summaries and caches them locally.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)

                    if viewModel.effectiveFeedMode != viewModel.settings.feedMode {
                        Text("No internet detected. Offline mode is active.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                Section("Personalization") {
                    Picker("Level", selection: Binding(
                        get: { viewModel.settings.personalizationLevel },
                        set: { viewModel.setPersonalization($0) }
                    )) {
                        ForEach(PersonalizationLevel.allCases, id: \.self) { level in
                            Text(level.rawValue).tag(level)
                        }
                    }
                    .pickerStyle(.segmented)

                    Text(personalizationDescription(viewModel.settings.personalizationLevel))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section("Appearance") {
                    Picker("Theme", selection: Binding(
                        get: { viewModel.settings.themeMode },
                        set: { viewModel.setTheme($0) }
                    )) {
                        ForEach(ThemeMode.allCases, id: \.self) { mode in
                            Text(mode.rawValue).tag(mode)
                        }
                    }

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(presets, id: \.self) { hex in
                                Circle()
                                    .fill(Color.fromHex(hex) ?? .green)
                                    .frame(width: 24, height: 24)
                                    .overlay(Circle().stroke(Color.primary.opacity(0.2), lineWidth: 1))
                                    .onTapGesture {
                                        accentHexDraft = hex
                                        viewModel.setAccentHex(hex)
                                    }
                            }
                        }
                        .padding(.vertical, 4)
                    }

                    ColorPicker("Accent color", selection: Binding(
                        get: { Color.fromHex(accentHexDraft) ?? .green },
                        set: { value in
                            if let hex = value.hexString {
                                accentHexDraft = hex
                                viewModel.setAccentHex(hex)
                            }
                        }
                    ))

                    TextField("Accent hex (#RRGGBB)", text: Binding(
                        get: { accentHexDraft },
                        set: { value in
                            accentHexDraft = value
                            viewModel.setAccentHex(value)
                        }
                    ))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                }

                Section("Accessibility") {
                    HStack {
                        Text("Font size")
                        Spacer()
                        Text("\(Int(viewModel.settings.fontScale * 100))%")
                            .foregroundStyle(.secondary)
                    }
                    Slider(
                        value: Binding(
                            get: { viewModel.settings.fontScale },
                            set: { viewModel.setFontScale($0) }
                        ),
                        in: 0.85 ... 1.35
                    )

                    Toggle("High contrast mode", isOn: Binding(
                        get: { viewModel.settings.highContrast },
                        set: { viewModel.setHighContrast($0) }
                    ))
                    Toggle("Reduce motion", isOn: Binding(
                        get: { viewModel.settings.reduceMotion },
                        set: { viewModel.setReduceMotion($0) }
                    ))
                }

                Section("Downloads") {
                    Toggle("Wi-Fi only downloads", isOn: Binding(
                        get: { viewModel.settings.wifiOnlyDownloads },
                        set: { viewModel.setWifiOnly($0) }
                    ))
                }

                Section("Settings backup") {
                    Button("Export settings (copy)") {
                        viewModel.exportSettingsToClipboard()
                    }

                    TextEditor(text: $importDraft)
                        .frame(minHeight: 120)
                        .font(.system(.footnote, design: .monospaced))

                    Button("Import settings") {
                        Task { await viewModel.importSettingsJSON(importDraft) }
                    }
                    .disabled(importDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }

                Section("Attribution") {
                    Text("This app uses Wikipedia content under CC BY-SA 4.0.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Link("Open CC BY-SA 4.0", destination: URL(string: "https://creativecommons.org/licenses/by-sa/4.0/")!)
                    Link("Open Wikipedia", destination: URL(string: "https://www.wikipedia.org/")!)
                }
            }
            .navigationTitle("Settings")
            .onAppear {
                if accentHexDraft.isEmpty {
                    accentHexDraft = viewModel.settings.accentHex
                }
            }
            .onChange(of: viewModel.settings.accentHex) { _, newValue in
                if accentHexDraft != newValue {
                    accentHexDraft = newValue
                }
            }
        }
    }
}

private func personalizationDescription(_ level: PersonalizationLevel) -> String {
    switch level {
    case .off:
        return "OFF: no behavior-based tuning. Feed remains mostly neutral."
    case .low:
        return "LOW: light personalization with strong diversity guardrails."
    case .medium:
        return "MEDIUM: balanced personalization and exploration."
    case .high:
        return "HIGH: stronger adaptation with anti-bubble constraints."
    }
}

private extension Color {
    static func fromHex(_ hex: String) -> Color? {
        let cleaned = hex
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")
        guard cleaned.count == 6 || cleaned.count == 8,
              let value = UInt64(cleaned, radix: 16) else {
            return nil
        }

        let r, g, b, a: Double
        if cleaned.count == 8 {
            r = Double((value & 0xFF00_0000) >> 24) / 255.0
            g = Double((value & 0x00FF_0000) >> 16) / 255.0
            b = Double((value & 0x0000_FF00) >> 8) / 255.0
            a = Double(value & 0x0000_00FF) / 255.0
        } else {
            r = Double((value & 0xFF00_00) >> 16) / 255.0
            g = Double((value & 0x00FF_00) >> 8) / 255.0
            b = Double(value & 0x0000_FF) / 255.0
            a = 1.0
        }
        return Color(.sRGB, red: r, green: g, blue: b, opacity: a)
    }

    var hexString: String? {
        let uiColor = UIColor(self)
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        guard uiColor.getRed(&red, green: &green, blue: &blue, alpha: &alpha) else {
            return nil
        }
        return String(
            format: "#%02X%02X%02X",
            Int(red * 255.0),
            Int(green * 255.0),
            Int(blue * 255.0)
        )
    }
}
