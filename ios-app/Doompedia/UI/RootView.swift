import SwiftUI

struct RootView: View {
    @ObservedObject var viewModel: MainViewModel

    var body: some View {
        TabView {
            FeedView(viewModel: viewModel)
                .tabItem {
                    Label("Feed", systemImage: "rectangle.stack")
                }

            SettingsView(viewModel: viewModel)
                .tabItem {
                    Label("Settings", systemImage: "gearshape")
                }

            AttributionView()
                .tabItem {
                    Label("Attribution", systemImage: "doc.text")
                }
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
