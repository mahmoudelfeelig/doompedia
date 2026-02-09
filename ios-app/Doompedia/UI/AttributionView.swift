import SwiftUI

struct AttributionView: View {
    var body: some View {
        NavigationStack {
            Form {
                Section("Required notice") {
                    Text(
                        "This app uses Wikipedia content under the Creative Commons Attribution-ShareAlike " +
                        "License (CC BY-SA). Wikipedia is a trademark of the Wikimedia Foundation. " +
                        "This app is not endorsed by or affiliated with the Wikimedia Foundation."
                    )
                }

                Section("Links") {
                    Link("CC BY-SA 4.0", destination: URL(string: "https://creativecommons.org/licenses/by-sa/4.0/")!)
                    Link("Wikipedia source", destination: URL(string: "https://en.wikipedia.org/")!)
                }
            }
            .navigationTitle("Attribution")
        }
    }
}
