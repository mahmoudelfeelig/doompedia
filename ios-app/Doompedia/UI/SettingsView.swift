import SwiftUI
import UIKit

struct SettingsView: View {
    @ObservedObject var viewModel: MainViewModel

    @State private var manifestURLDraft: String = ""
    @State private var accentHexDraft: String = ""
    @State private var importDraft: String = ""

    private let presets = [
        "#0B6E5B",
        "#1363DF",
        "#C44536",
        "#F59E0B",
        "#7E22CE",
        "#1F2937",
    ]

    var body: some View {
        NavigationStack {
            Form {
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
                                    .overlay(
                                        Circle()
                                            .stroke(Color.primary.opacity(0.2), lineWidth: 1)
                                    )
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

                Section("Data and updates") {
                    Toggle("Wi-Fi only", isOn: Binding(
                        get: { viewModel.settings.wifiOnlyDownloads },
                        set: { viewModel.setWifiOnly($0) }
                    ))

                    TextField("Manifest URL", text: Binding(
                        get: { manifestURLDraft },
                        set: { value in
                            manifestURLDraft = value
                            viewModel.setManifestURL(value)
                        }
                    ))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()

                    Button(viewModel.isUpdatingPack ? "Checking..." : "Check updates now") {
                        Task { await viewModel.checkForUpdatesNow() }
                    }
                    .disabled(viewModel.isUpdatingPack)

                    Text("Installed pack version: \(viewModel.settings.installedPackVersion)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    if !viewModel.settings.lastUpdateISO.isEmpty {
                        Text("Last check: \(viewModel.settings.lastUpdateISO)")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    if !viewModel.settings.lastUpdateStatus.isEmpty {
                        Text(viewModel.settings.lastUpdateStatus)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
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
                if manifestURLDraft.isEmpty {
                    manifestURLDraft = viewModel.settings.manifestURL
                }
                if accentHexDraft.isEmpty {
                    accentHexDraft = viewModel.settings.accentHex
                }
            }
            .onChange(of: viewModel.settings.manifestURL) { _, newValue in
                if newValue != manifestURLDraft {
                    manifestURLDraft = newValue
                }
            }
            .onChange(of: viewModel.settings.accentHex) { _, newValue in
                if newValue != accentHexDraft {
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
