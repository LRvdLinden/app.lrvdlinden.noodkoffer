import SwiftUI

/// "What to do when…": short, offline guidance based on the government's Denk vooruit advice.
struct InfoTopic: Identifiable {
    let id: String
    let title: LocalizedStringResource
    let symbol: String
    let steps: [LocalizedStringResource]

    static let all: [InfoTopic] = [
        InfoTopic(id: "nlalert", title: "NL-Alert or siren", symbol: "exclamationmark.triangle.fill", steps: [
            "Read the NL-Alert message and follow its instructions.",
            "Siren: go inside, close windows and doors, and turn off mechanical ventilation.",
            "Follow the news on your regional emergency broadcaster and on crisis.nl.",
            "Warn the people around you, such as neighbours and visitors.",
        ]),
        InfoTopic(id: "power", title: "Power cut", symbol: "bolt.slash.fill", steps: [
            "Use a torch rather than candles, and never leave candles unattended.",
            "Keep the fridge and freezer closed as much as possible.",
            "Switch off appliances so they do not all start at once when the power returns.",
            "Listen to the emergency broadcaster on a battery or wind-up radio.",
            "Check on neighbours who may need help.",
        ]),
        InfoTopic(id: "water", title: "No tap water", symbol: "drop.triangle.fill", steps: [
            "Use your stored drinking water: 3 litres per person per day.",
            "Use water sparingly; wet wipes and hand gel help with hygiene.",
            "When the tap works again, follow the advice of your water company, for example boiling water first.",
        ]),
        InfoTopic(id: "weather", title: "Storm, extreme weather or flooding", symbol: "cloud.bolt.rain.fill", steps: [
            "Stay indoors and keep away from windows during a storm.",
            "Put important papers and your emergency kit somewhere high and easy to reach.",
            "Follow the instructions of the municipality and the emergency services; leave when you are told to.",
        ]),
        InfoTopic(id: "comms", title: "No phone or internet", symbol: "antenna.radiowaves.left.and.right.slash", steps: [
            "Use your printed emergency plan: meeting point and phone numbers are on it.",
            "Go to the agreed meeting point if you cannot reach each other.",
            "In a life-threatening situation, go to a fire station, police station or hospital if 112 does not work.",
        ]),
        InfoTopic(id: "numbers", title: "Important numbers", symbol: "phone.fill", steps: [
            "112: emergencies where every second counts.",
            "0900-8844: police, when it is not urgent.",
            "Your municipality: often 14 plus the area code (for example 14 070 in The Hague).",
        ]),
    ]
}

struct InfoDetailView: View {
    let topic: InfoTopic
    var body: some View {
        List {
            Section {
                ForEach(Array(topic.steps.enumerated()), id: \.offset) { index, step in
                    HStack(alignment: .firstTextBaseline, spacing: 12) {
                        Text(verbatim: "\(index + 1)")
                            .font(.subheadline.weight(.heavy)).foregroundStyle(Theme.purple)
                            .frame(width: 28, height: 28)
                            .background(Theme.yellow, in: Circle())
                        Text(step)
                    }
                    .padding(.vertical, 2)
                }
            } footer: {
                Text("Based on the advice of the Dutch government (denkvooruit.nl). Noodpakket is an independent app.")
            }
        }
        .brandNavigationBar()
        .navigationTitle(Text(topic.title))
        .brandInlineTitle(Text(topic.title))
    }
}
