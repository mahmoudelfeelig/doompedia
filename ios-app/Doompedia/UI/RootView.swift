import SwiftUI

struct RootView: View {
    @ObservedObject var viewModel: MainViewModel
    @State private var selectedTab: AppTab = .explore

    var body: some View {
        TabView(selection: Binding(
            get: { selectedTab },
            set: { newValue in
                if selectedTab == newValue, newValue == .explore {
                    Task { await viewModel.refreshFeed() }
                }
                selectedTab = newValue
            }
        )) {
            FeedView(viewModel: viewModel)
                .tag(AppTab.explore)
                .tabItem {
                    Label("Explore", systemImage: "rectangle.stack")
                }

            SavedView(viewModel: viewModel)
                .tag(AppTab.saved)
                .tabItem {
                    Label("Saved", systemImage: "bookmark")
                }

            PacksView(viewModel: viewModel)
                .tag(AppTab.packs)
                .tabItem {
                    Label("Packs", systemImage: "square.and.arrow.down")
                }

            SettingsView(viewModel: viewModel)
                .tag(AppTab.settings)
                .tabItem {
                    Label("Settings", systemImage: "gearshape")
                }
        }
        .sheet(item: Binding(
            get: { viewModel.folderPickerCard },
            set: { if $0 == nil { viewModel.dismissFolderPicker() } }
        )) { card in
            FolderPickerSheet(viewModel: viewModel, card: card)
        }
        .alert("Doompedia", isPresented: Binding(
            get: { viewModel.message != nil },
            set: { if !$0 { viewModel.message = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.message ?? "")
        }
    }
}

private enum AppTab: Hashable {
    case explore
    case saved
    case packs
    case settings
}
