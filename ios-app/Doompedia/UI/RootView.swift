import SwiftUI

struct RootView: View {
    @ObservedObject var viewModel: MainViewModel

    var body: some View {
        TabView {
            FeedView(viewModel: viewModel)
                .tabItem {
                    Label("Feed", systemImage: "rectangle.stack")
                }

            SavedView(viewModel: viewModel)
                .tabItem {
                    Label("Saved", systemImage: "bookmark")
                }

            SettingsView(viewModel: viewModel)
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
