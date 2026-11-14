import SwiftUI

/// Product picture for an item: the scanned product photo when there is one, otherwise the illustration
/// for the catalog item on a soft tile in the category colour.
struct ItemArt: View {
    let item: KitItem
    var size: CGFloat = 56

    var body: some View {
        if let url = item.imageURL.flatMap(URL.init(string:)) {
            AsyncImage(url: url) { image in
                image.resizable().scaledToFit().padding(size * 0.08)
            } placeholder: {
                ArtImage(key: item.catalogKey ?? "other").padding(size * 0.1)
            }
            .frame(width: size, height: size)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: size * 0.26, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: size * 0.26, style: .continuous).strokeBorder(.black.opacity(0.06)))
            .accessibilityHidden(true)
        } else {
            ArtTile(key: item.catalogKey ?? "other", category: item.category, size: size)
        }
    }
}

/// Illustration on its category tile.
struct ArtTile: View {
    let key: String
    let category: KitCategory
    var size: CGFloat = 56

    var body: some View {
        let (a, b) = Theme.tile(category.rawValue)
        ArtImage(key: key)
            .padding(size * 0.08)
            .frame(width: size, height: size)
            .background(LinearGradient(colors: [a, b], startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: size * 0.26, style: .continuous))
            .accessibilityHidden(true)
    }
}

/// The vector illustration from the asset catalog ("art-<key>").
struct ArtImage: View {
    let key: String
    var body: some View {
        Image(Catalog.entry(for: key) != nil || key == "other" ? "art-\(key)" : "art-other")
            .resizable()
            .scaledToFit()
            .accessibilityHidden(true)
    }
}

/// Thin rounded progress bar.
struct FillBar: View {
    let fraction: Double
    var color: Color = Theme.ok
    var track: Color = Theme.accent.opacity(0.12)
    var height: CGFloat = 6

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                Capsule().fill(color)
                    .frame(width: max(height, geo.size.width * min(1, max(0, fraction))))
                    .opacity(fraction > 0 ? 1 : 0)
            }
        }
        .frame(height: height)
        .animation(.easeOut(duration: 0.5), value: fraction)
        .accessibilityHidden(true)
    }
}

/// Small coloured label.
struct Pill: View {
    let text: Text
    let color: Color
    var body: some View {
        text
            .font(.caption.weight(.bold))
            .padding(.horizontal, 9).padding(.vertical, 4)
            .foregroundStyle(color)
            .background(color.opacity(0.14), in: Capsule())
    }
}

/// Purple header used at the top of the main screens: title, subtitle, optional content and illustrations.
struct HeroHeader<Content: View, Actions: View>: View {
    let title: LocalizedStringKey
    var subtitle: Text?
    var art: [String] = []
    @ViewBuilder var content: Content
    @ViewBuilder var actions: Actions

    var body: some View {
        ZStack(alignment: .topTrailing) {
            if !art.isEmpty { heroArt.padding(.top, 56).offset(x: 8) }
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(title)
                        .font(.system(.largeTitle, design: .rounded).weight(.heavy))
                        .foregroundStyle(.white)
                        .accessibilityAddTraits(.isHeader)
                    Spacer()
                    HStack(spacing: 10) { actions }
                }
                if let subtitle {
                    subtitle.font(.subheadline).foregroundStyle(.white.opacity(0.75))
                }
                content.padding(.top, 14)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 34)
    }

    /// Up to three illustrations, slightly rotated, on the right of the header.
    private var heroArt: some View {
        ZStack(alignment: .topTrailing) {
            if art.count > 2 { ArtImage(key: art[2]).frame(width: 76).rotationEffect(.degrees(5)).offset(x: -4, y: 0) }
            if art.count > 1 { ArtImage(key: art[1]).frame(width: 100).rotationEffect(.degrees(7)).offset(y: 96) }
            ArtImage(key: art[0]).frame(width: 88).rotationEffect(.degrees(-8)).offset(x: -74, y: 42)
        }
        .frame(width: 170, height: 200, alignment: .topTrailing)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// Round white button for the header (scan, add, share).
struct HeaderButton: View {
    let symbol: String
    let label: LocalizedStringKey
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.headline)
                .foregroundStyle(Theme.purple)
                .frame(width: 40, height: 40)
                .background(Theme.yellow, in: Circle())
        }
        .accessibilityLabel(Text(label))
    }
}

/// Page with the purple header on top and the rounded canvas sheet below it.
struct BrandPage<Header: View, Sheet: View>: View {
    @ViewBuilder var header: Header
    @ViewBuilder var sheet: Sheet

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                header
                VStack(alignment: .leading, spacing: 0) { sheet }
                    .padding(.horizontal, 16)
                    .padding(.top, 18)
                    .padding(.bottom, 30)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.canvas, in: UnevenRoundedRectangle(topLeadingRadius: 30, topTrailingRadius: 30, style: .continuous))
            }
            .frame(maxWidth: 760)
            .frame(maxWidth: .infinity)
        }
        .background {
            VStack(spacing: 0) {
                LinearGradient(colors: [Theme.purple, Theme.purpleDeep], startPoint: .top, endPoint: .bottom)
                    .frame(height: 520)
                Theme.canvas
            }
            .ignoresSafeArea()
        }
        .toolbar(.hidden, for: .navigationBar)
    }
}

/// Section title inside the canvas sheet.
struct SheetSectionTitle: View {
    let title: Text
    var body: some View {
        title
            .font(.caption.weight(.heavy))
            .tracking(0.6)
            .textCase(.uppercase)
            .foregroundStyle(.secondary)
            .padding(.horizontal, 4)
            .padding(.top, 14)
            .padding(.bottom, 8)
            .accessibilityAddTraits(.isHeader)
    }
}

/// White rounded card row container.
struct CardRow<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        HStack(spacing: 12) { content }
            .padding(8)
            .padding(.trailing, 6)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .padding(.bottom, 8)
    }
}

/// Purple navigation bar for the form screens (plan, settings).
extension View {
    /// Root screen of a tab: our own purple header with a large white title instead of the system navigation bar,
    /// whose large title did not show on the purple background.
    func brandRootTitle(_ title: LocalizedStringKey) -> some View {
        self
            .navigationTitle(title)
            .toolbar(.hidden, for: .navigationBar)
            .safeAreaInset(edge: .top, spacing: 0) {
                Text(title)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .accessibilityAddTraits(.isHeader)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20)
                    .padding(.top, 6)
                    .padding(.bottom, 16)
                    .background(Theme.purple.ignoresSafeArea(edges: .top))
            }
    }

    /// Pushed screen: inline title, always drawn in white on the purple bar.
    func brandInlineTitle(_ title: Text) -> some View {
        self
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    title.font(.system(.headline, design: .rounded).weight(.semibold)).foregroundStyle(.white).lineLimit(1)
                }
            }
    }

    func brandNavigationBar() -> some View {
        self
            .toolbarBackground(Theme.purple, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .scrollContentBackground(.hidden)
            .background(Theme.canvas)
    }
}
