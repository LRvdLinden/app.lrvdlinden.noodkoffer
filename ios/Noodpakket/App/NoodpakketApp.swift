import SwiftUI
import SwiftData

@main
struct NoodpakketApp: App {
    @AppStorage("ui.appearance") private var appearance: AppearanceSetting = .system

    let container: ModelContainer = {
        let schema = Schema([KitItem.self, Household.self, EmergencyPlan.self, ScannedProduct.self])
        let cloud = ModelConfiguration(schema: schema, cloudKitDatabase: .private("iCloud.app.lrvdlinden.noodpakket"))
        if let container = try? ModelContainer(for: schema, configurations: cloud) { return container }
        // Fallback without iCloud (e.g. not signed in to iCloud): keep working locally.
        let local = ModelConfiguration(schema: schema, cloudKitDatabase: .none)
        do { return try ModelContainer(for: schema, configurations: local) }
        catch { fatalError("Could not create the data store: \(error)") }
    }()

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(appearance.colorScheme)
                .tint(Theme.accent)
        }
        .modelContainer(container)
    }
}
