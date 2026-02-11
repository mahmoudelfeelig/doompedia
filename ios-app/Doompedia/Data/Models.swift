import Foundation

enum PersonalizationLevel: String, CaseIterable, Codable {
    case off = "OFF"
    case low = "LOW"
    case medium = "MEDIUM"
    case high = "HIGH"
}

enum ThemeMode: String, CaseIterable, Codable {
    case system = "SYSTEM"
    case light = "LIGHT"
    case dark = "DARK"
}

enum FeedMode: String, CaseIterable, Codable {
    case offline = "OFFLINE"
    case online = "ONLINE"
}

enum ReadSort: String, CaseIterable, Codable {
    case newestFirst = "NEWEST_FIRST"
    case oldestFirst = "OLDEST_FIRST"
}

struct UserSettings: Codable {
    var language: String = "en"
    var personalizationLevel: PersonalizationLevel = .low
    var feedMode: FeedMode = .offline
    var themeMode: ThemeMode = .system
    var accentHex: String = "#0B6E5B"
    var fontScale: Double = 1.0
    var highContrast: Bool = false
    var reduceMotion: Bool = false
    var readSort: ReadSort = .newestFirst
    var wifiOnlyDownloads: Bool = true
    var downloadPreviewImages: Bool = false
    var manifestURL: String = ""
    var installedPackVersion: Int = 0
    var lastUpdateISO: String = ""
    var lastUpdateStatus: String = ""
    var customPacksJSON: String = "[]"

    private enum CodingKeys: String, CodingKey {
        case language
        case personalizationLevel
        case feedMode
        case themeMode
        case accentHex
        case fontScale
        case highContrast
        case reduceMotion
        case readSort
        case wifiOnlyDownloads
        case downloadPreviewImages
        case manifestURL
        case installedPackVersion
        case lastUpdateISO
        case lastUpdateStatus
        case customPacksJSON
    }

    init() {}

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        language = try container.decodeIfPresent(String.self, forKey: .language) ?? "en"
        personalizationLevel = try container.decodeIfPresent(PersonalizationLevel.self, forKey: .personalizationLevel) ?? .low
        feedMode = try container.decodeIfPresent(FeedMode.self, forKey: .feedMode) ?? .offline
        themeMode = try container.decodeIfPresent(ThemeMode.self, forKey: .themeMode) ?? .system
        accentHex = try container.decodeIfPresent(String.self, forKey: .accentHex) ?? "#0B6E5B"
        fontScale = try container.decodeIfPresent(Double.self, forKey: .fontScale) ?? 1.0
        highContrast = try container.decodeIfPresent(Bool.self, forKey: .highContrast) ?? false
        reduceMotion = try container.decodeIfPresent(Bool.self, forKey: .reduceMotion) ?? false
        readSort = try container.decodeIfPresent(ReadSort.self, forKey: .readSort) ?? .newestFirst
        wifiOnlyDownloads = try container.decodeIfPresent(Bool.self, forKey: .wifiOnlyDownloads) ?? true
        downloadPreviewImages = try container.decodeIfPresent(Bool.self, forKey: .downloadPreviewImages) ?? false
        manifestURL = try container.decodeIfPresent(String.self, forKey: .manifestURL) ?? ""
        installedPackVersion = try container.decodeIfPresent(Int.self, forKey: .installedPackVersion) ?? 0
        lastUpdateISO = try container.decodeIfPresent(String.self, forKey: .lastUpdateISO) ?? ""
        lastUpdateStatus = try container.decodeIfPresent(String.self, forKey: .lastUpdateStatus) ?? ""
        customPacksJSON = try container.decodeIfPresent(String.self, forKey: .customPacksJSON) ?? "[]"
    }
}

struct SaveFolderSummary: Identifiable, Hashable {
    let folderId: Int64
    let name: String
    let isDefault: Bool
    let articleCount: Int

    var id: Int64 { folderId }
}

struct ArticleCard: Identifiable, Hashable {
    let pageId: Int64
    let lang: String
    let title: String
    let normalizedTitle: String
    let summary: String
    let wikiURL: String
    let topicKey: String
    let qualityScore: Double
    let isDisambiguation: Bool
    let sourceRevId: Int64?
    let updatedAt: String
    let bookmarked: Bool

    var id: Int64 { pageId }
}

struct RankedCard: Identifiable, Hashable {
    let card: ArticleCard
    let score: Double
    let why: String

    var id: Int64 { card.pageId }
}

func normalizeSearch(_ input: String) -> String {
    let nfkc = input.precomposedStringWithCompatibilityMapping
    let compact = nfkc
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
    return compact.lowercased(with: Locale(identifier: "en_US_POSIX"))
}

func editDistanceAtMostOne(_ left: String, _ right: String) -> Bool {
    if left == right { return true }
    let leftChars = Array(left)
    let rightChars = Array(right)
    let leftCount = leftChars.count
    let rightCount = rightChars.count

    if abs(leftCount - rightCount) > 1 {
        return false
    }

    var i = 0
    var j = 0
    var edits = 0

    while i < leftCount && j < rightCount {
        if leftChars[i] == rightChars[j] {
            i += 1
            j += 1
            continue
        }

        edits += 1
        if edits > 1 { return false }

        if leftCount > rightCount {
            i += 1
        } else if rightCount > leftCount {
            j += 1
        } else {
            i += 1
            j += 1
        }
    }

    if i < leftCount || j < rightCount {
        edits += 1
    }

    return edits <= 1
}
