import SwiftUI

@main
struct DoompediaApp: App {
    @StateObject private var viewModel = MainViewModel.make()

    var body: some Scene {
        WindowGroup {
            RootView(viewModel: viewModel)
                .preferredColorScheme(viewModel.colorScheme)
        }
    }
}
