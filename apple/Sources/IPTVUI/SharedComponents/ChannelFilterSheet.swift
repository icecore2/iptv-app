import SwiftUI
import IPTVCore

/// Filter and sorting configuration sheet for the channel guide.
public struct ChannelFilterSheet: View {
    public let categories: [String]
    public let categoryCounts: [String: Int]
    @Binding public var selectedCategory: String
    @Binding public var onlyWithEpg: Bool
    @Binding public var onlyLiveNow: Bool
    @Binding public var sortBy: ChannelSortOrder
    public let onReset: () -> Void
    public let onDismiss: () -> Void

    public init(
        categories: [String],
        categoryCounts: [String: Int],
        selectedCategory: Binding<String>,
        onlyWithEpg: Binding<Bool>,
        onlyLiveNow: Binding<Bool>,
        sortBy: Binding<ChannelSortOrder>,
        onReset: @escaping () -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.categories = categories
        self.categoryCounts = categoryCounts
        self._selectedCategory = selectedCategory
        self._onlyWithEpg = onlyWithEpg
        self._onlyLiveNow = onlyLiveNow
        self._sortBy = sortBy
        self.onReset = onReset
        self.onDismiss = onDismiss
    }

    public var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("Sort By")) {
                    Picker("Sort Order", selection: $sortBy) {
                        ForEach(ChannelSortOrder.allCases) { order in
                            Text(order.label).tag(order)
                        }
                    }
                    .pickerStyle(.menu)
                }

                Section(header: Text("EPG Filters")) {
                    Toggle("Only channels with EPG Guide", isOn: $onlyWithEpg)
                    Toggle("Only channels with active Live Programme", isOn: $onlyLiveNow)
                }

                Section(header: Text("Category / Group")) {
                    Picker("Category", selection: $selectedCategory) {
                        ForEach(categories, id: \.self) { cat in
                            let count = categoryCounts[cat] ?? 0
                            Text("\(cat) (\(count))").tag(cat)
                        }
                    }
                    .pickerStyle(.menu)
                }

                Section {
                    Button("Reset All Filters", role: .destructive) {
                        onReset()
                    }
                }
            }
            .navigationTitle("Filter Channels")
            .inlineTitleMode()
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", action: onDismiss)
                }
            }
        }
    }
}
