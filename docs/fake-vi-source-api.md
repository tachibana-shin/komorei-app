# API surface of the Komorei Fake source (VI)

> Source file: `sources/sources/vi.fake-source/src/lib.rs` (1273 lines)
> Purpose: list the **fields / functions the source exports** and cross-check **how much the app consumes them**, to track implementation progress. Update when the source or the app changes.

## Status legend

| Symbol | Meaning |
|---|---|
| ✅ | App fully consumes it (mapping + working UI) |
| ◐ | Model/mapping exists but no UI yet (or unconfirmed) |
| ❌ | Not consumed / display-only / no equivalent place in the app |
| ➖ | No-op on the source side (no real effect) |

---

## 1. Metadata

| Item | Value | Line |
|---|---|---|
| `SOURCE_ID` | `vi.fake-source` | L19 |
| `PAGE_SIZE` | `15` | L20 |
| Sample HLS | `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` | L22 |
| Sample MP4 720p | `.../Big_Buck_Bunny_720_10s_5MB.mp4` | L23–24 |
| Sample MP4 FHD | `.../BigBuckBunny.mp4` | L25–26 |
| `ALL_GENRES` (12) | Hành Động, Chuyển Sinh, Phiêu Lưu, Harem, Shounen, Lãng Mạn, Siêu Nhiên, Học Đường, Hài Hước, Bí Ẩn, Giả Tưởng, Mecha | L32–45 |
| `languages` (manifest) | `["vi", "en"]` | `res/source.json` L8–10 |

> `languages: ["vi","en"]` — the sources list shows the **multi-language** badge (`SourceLabels.kt` when `languages.size > 1`); it also enables the **app-injected language picker** on the source Settings screen (see §4.11 + checklist).

## 2. Registered traits — `register_source!` (L1241)

| Trait | Status | App-side notes |
|---|---|---|
| `Source` | ✅ | Goes through the runner (`registry.call`) |
| `ListingProvider` | ✅ | `get_anime_list` (4 listings) → `AnimeRepository.getListing` → Listing screen (grid + infinite scroll) |
| `Home` | ✅ | `HomeScreen` renders all 7 components |
| `DynamicFilters` | ✅ | Per-source search screen (`SourceSearchScreen`) — Aidoku `SearchViewController` + `FilterHeaderView` port: 🔍 button before chips → query + filters debounced 300ms, paginated |
| `DynamicSettings` | ✅ | `get_settings` → `SourceSettingsScreen` (toggle + persist via `KrxDefaultsStore`, key scoped `{sourceId}.`) |
| `DynamicListings` | ✅ | `get_dynamic_listings` → `AnimeRepository.getListings` + Aidoku-style chips row on Home (`ListingChipsRow`): tapping a chip swaps the content below to an inline paginated listing (`HomeListingGrid`) |
| `NotificationHandler` | ✅ | The app calls it when a setting that declares `notification` changes (see §4.11/§3) via `handleNotification` → runner `notify` → wasm; the source mirrors the key into defaults `last_notification` (verifiable round-trip) |
| `DeepLinkHandler` | ✅ | `MainActivity` intent-filter (`komorei://komorei.example/…`) → `DeepLinkManager` → `DeepLinkViewModel` → `DeepLinkResolver` (asks each source, preferring a host match) → opens the player (`/anime/`, `/watch/`) or a listing route (`/list/`) |
| `MigrationHandler` | ✅ | "Migrate source data" (source Settings) → `SourceMigrationRepository` re-keys bookmarks + watch history through the runner `migrate_anime`/`migrate_episode` (wasm combined export `handle_key_migration`, key_kind 0/1) |
| `SegmentUrlInterceptor` | ✅ | The app wraps via `TransformableHttpDataSource` (`data/remote/`) |
| `SegmentDataInterceptor` | ✅ | As above |

## 3. Exported functions (per-trait methods)

| Function | Signature | Description | App consumption |
|---|---|---|---|
| `get_search_anime_list` (L759) | `(query: Option<String>, page: i32, filters: Vec<FilterValue>) -> AnimePageResult` | Search by title/original_title + filters + sort, paginated 15 | ✅ `AnimeRepository.search` → per-source search (`SourceSearchViewModel`) + global search tab Discover (`SearchViewModel` → `searchMultiSource`) |
| `get_anime_update` (L794) | `(anime, needs_details, needs_chapters) -> Anime` | `needs_details` → full version (`copy_from`); `needs_chapters` → episodes of the **current season key only** | ✅ `AnimeRepository.getAnimeUpdate` (player + detail) |
| `get_stream_list` (L813) | `(anime, episode) -> Vec<StreamInfo>` | 3 servers: `hls`, `mp4_720`, `mp4_fhd`; reads the `prefer_fhd` default to reorder | ✅ `getStreamList` → server picker |
| `get_stream` (L842) | `(anime, episode, stream) -> StreamData` | Picks URL/type by `stream.key`; includes headers, vi subtitle, intro/outro | ✅ `getStream` → player (Media3) |
| `get_anime_list` (L895) | `(listing, page) -> AnimePageResult` | `ongoing` / `completed` / `popular` (sort views) / `latest`=all | ✅ `AnimeRepository.getListing` → Listing screen (15/page) |
| `get_home` (L926) | `() -> HomeLayout` | 7 components (see §5.6) | ✅ `getHome` cache + `HomeScreen` |
| `get_dynamic_filters` (L1093) | `() -> Vec<Filter>` | 5 filters + 1 note (see §4.8) | ✅ `SourceSearchScreen`: header pills + aggregate sheet + debounced search |
| `get_dynamic_settings` (L1140) | `() -> Vec<Setting>` | 2 toggles: `prefer_fhd`, `show_intro` + 1 button `clear_cache` | ✅ Settings screen renders + persists (persist → the runner reads back via `defaults_get`); the toggle/button `notification` is forwarded to `handle_notification`; `show_intro` has no runtime effect yet |
| `get_dynamic_listings` (L1160) | `() -> Vec<Listing>` | latest / popular / ongoing / completed | ✅ chips `[Home]+listings` on each source's Home; tapping swaps content inline (HomeViewModel: `loadListings`/`selectListing`/`loadListingPage`) |
| `handle_notification` (L1186) | `(key: String)` | The app sends it after every setting change that declares `notification` (toggle + button, Aidoku-style): `AnimeRepository.handleNotification` → `runner.notify` → wasm; the source mirrors the key into defaults `last_notification` + counts `clear_cache` | ✅ round-trip (settings screen → wasm → defaults, verified by tests) |
| `handle_deep_link` (L1182) | `(url) -> Option<DeepLinkResult>` | `/anime/<key>` · `/watch/<anime>/<ep>` · `/list/<id>` | ✅ `AnimeRepository.handleDeepLink` → `DeepLinkResolver` (deep link) → `DeepLinkViewModel` → player sheet / listing route |
| `handle_key_migration` (L1238) | `(key_kind: 0\|1, anime_key, episode_key) -> String` | Identity — the **single combined export** of MigrationHandler: `key_kind=0` → `handle_anime_migration(key)`, `key_kind=1` → `handle_episode_migration(anime_key, episode_key)` (there is NO separate `handle_anime_migration`/`handle_episode_migration` export like deep_link); the runner calls `migrate_anime`/`migrate_episode` with `call3` | ✅ `SourceMigrationRepository` ("Migrate source data" in source Settings) asks each anime/episode key in `anime_library` + `watch_history` and rewrites the rows whose key changed — test `SourceMigrationRepositoryTest` |
| `intercept_segment_url` (L1225) | `(opt StreamData, url) -> String` | Passthrough | ✅ plumbing ready |
| `intercept_segment_data` (L1228) | `(opt StreamData, url, &[u8]) -> Vec<u8>` | Passthrough | ✅ plumbing ready |

## 4. Data model (main structs)

### 4.1 `Anime` — **full** version (`build_anime`, L603)

| Runner field | Type | App consumption (`model/Anime.kt`) | Status |
|---|---|---|---|
| `key` | String | `id` | ✅ |
| `source_id` | String | `sourceId` | ✅ |
| `title` | String | `title` | ✅ |
| `original_title` | String | `originalTitle` | ✅ |
| `cover` | String | `posterUrl` | ✅ |
| `banner` | Option<String> | `bannerUrl` | ✅ (ImageScroller/BigScroller) |
| `description` | Option<String> | `description` | ✅ (detail) |
| `episode_count` | i32 | `episodeCount` | ✅ |
| `current_episode` | Option<String> | `currentEpisode` | ✅ (badge "Tập…/Full…") |
| `rating` | Option<f32> | `rating` | ✅ (⭐ AnimeCard) |
| `rating_count` | Option<i32> | `ratingCount` | ✅ |
| `status` | AnimeStatus | `status` | ✅ (Ongoing/Completed) |
| `release_year` | Option<CategoryLink> | `releaseYear` | ✅ ("1999 • Genre" AnimeCard) |
| `genres` | Vec<CategoryLink> | `genres` | ✅ |
| `authors` | Vec<CategoryLink> | `authors` | ✅ (detail) |
| `studio` | Option<CategoryLink> | `studio` | ✅ (detail) |
| `season_of` | Option<CategoryLink> | `seasonOf` | ◐ always `None` from the source |
| `countries` | Vec<CategoryLink> | `countries` | ◐ always empty |
| `is_featured` | bool | `isFeatured` | ✅ (BigScroller + hero) |
| `views` | i32 | `views` | ✅ (sort "popular"; the number is not shown yet) |
| `next_episode_air_info` | Option<String> | `nextEpisodeAirInfo` | ✅ (detail) |
| `quality_tag` | Option<String> | `qualityTag` | ✅ (QualityTagBadge on every poster/thumb/banner) |
| `seasons` | Vec<AnimeSeason> | `seasons` | ✅ (season picker/chunk) |
| `episodes` | Option<Vec<Episode>> | `episodes` | ✅ (only when `needs_chapters`) |
| `url` | Option<String> | — | ❌ the app model has no matching field |

### 4.2 `Anime` — **lite** version (`build_lite`, L653)

`key, source_id, title, original_title, cover, episode_count, current_episode, rating, rating_count, views, release_year, status, quality_tag, genres, is_featured` — the rest defaults.

> ⚠️ **Lite is NOT stripped for display**: it still carries `quality_tag`, `rating`, `current_episode`, `release_year`, `genres`, `status` — so home cards/badges render immediately without an upgrade.

### 4.3 `Episode` (`generate_episodes`, L684 — capped at 64 episodes for mega series)

| Field | Type | App consumption | Status |
|---|---|---|---|
| `key` | String | `key` | ✅ |
| `episode_number` | **String** | `episodeNumber` (String) | ✅ |
| `title` | Option<String> | | ✅ |
| `thumbnail` | Option<String> | | ✅ |
| `date_uploaded` | Option<i64> (**seconds**) | `dateUploaded` | ✅ (home `latest` uses **milliseconds**) |
| `duration_seconds` | Option<i64> = 1440 | | ✅ |
| `quality` | Option<String> | | ✅ |
| `url` | Option<String> = None | | ◐ |
| `language` | Some("vi") | | ◐ |
| `locked` | bool = false | | ◐ |

### 4.4 Stream — `StreamInfo` / `StreamData` / `SubtitleInfo` / `RangeLong`

| Struct | Field | Consumption | Status |
|---|---|---|---|
| `StreamInfo` | `key, name, quality` | ✅ server picker |
| `StreamData` | `url, stream_type (HLS/MP4), is_content, headers, subtitles` | `is_content=false` → no media source built | ✅ |
| `StreamData` | `intro / outro: Option<RangeLong>` | Skip intro/outro overlay (player) | ✅ |
| `SubtitleInfo` | `url, language, label, headers` | → player subtitle track | ◐ |
| `RangeLong` | `start_ms, end_ms` | Skip hint `introRange/outroRange` | ✅ |

### 4.5 `AnimePageResult`

| Field | Consumption | Status |
|---|---|---|
| `entries: Vec<Anime>` | Search/listing pagination | ✅ (search + listing) |
| `has_next_page: bool` | Pager loads the next page | ✅ |

### 4.6 `Link` + `LinkValue` (L937–1007)

| Field/Variant | Consumption | Status |
|---|---|---|
| `title, subtitle, image_url` | | ✅ |
| `LinkValue::Anime` | Tap → detail | ✅ |
| `LinkValue::Listing` | "See all…" / genre link | ✅ tap → Listing screen (route `listing/{sourceId}/{listingArg}`, listing = URL-safe JSON) |
| `LinkValue::Url` | "Source page" link | ❌ display-only |

### 4.7 `HomeComponentValue` — the 7 components of `get_home` (L1012–1085)

| Component | Fields | App render | Status |
|---|---|---|---|
| `ImageScroller` ("Khám Phá") | `links, auto_scroll_interval=4.0, width=800, height=450` | `ImageScrollerRow` | ✅ |
| `BigScroller` ("Nổi Bật") | `entries (full), auto_scroll_interval=5.0` | `BigScrollerRow` | ✅ |
| `Scroller` ("Đang Hot") | `entries (8 lite), listing="hot"` | `ScrollerRow` (AnimeCard rail) | ✅ |
| `AnimeEpisodeList` ("Mới Cập Nhật") | `page_size=Some(4), entries (10), listing="latest"` | `AnimeEpisodeListRow` — `None`=vertical list, `Some(n)`=paged grid | ✅ |
| `AnimeList` ("Phổ Biến Nhất") | `ranking=true, page_size=Some(6), entries (12), listing="popular"` | `AnimeListRow` — paged 2 columns, fixed `pagedCellWidth()` cap 180dp | ✅ |
| `Filters` ("Thể Loại") | `Vec<FilterItem>` (12 genres → `MultiSelect id="genres"`) | `FiltersRow` (chips) | ✅ tap chip → genre search |
| `Links` ("Liên Kết") | 4 links: latest/ongoing/completed/Url | `LinksRow` | ✅ Listing/Anime taps navigate; Url display-only |

### 4.8 `Filter` — 5 kinds (L1094–1132)

| Kind | Field | Consumption | Status |
|---|---|---|---|
| `TextFilter` | `id="search"`, title/placeholder | `TextFilterRow` in the aggregate sheet (value → `FilterValue.Text`) | ✅ |
| `SortFilter` | `id="sort"`, options Đánh giá/Phổ biến/A-Z | `SortFilterPill` dropdown (⇅ arrow when `canAscend`) + `SortFilterGroup` (runs through `sort_entries` L735) | ✅ |
| `MultiSelectFilter` | `id="genres"`, `is_genre=true`, `uses_tag_style=true`, 12 options | Genre chip home + `searchMultiSource` + `MultiSelectFilterPill` tag chips (✓ + badge count) | ✅ |
| `SelectFilter` | `id="status"`, options Tất cả/Đang phát/Hoàn thành | `SelectFilterPill` + `SelectFilterGroup` → `matches_filter` L715 | ✅ |
| `RangeFilter` | `id="year"`, min 1996, max 2025, `decimal=false` | `RangeFilterRow` (From/To number fields, from≤to) in the aggregate sheet | ✅ |
| `Filter::note` | "Nguồn dữ liệu demo — Komorei Fake (VI)" | Shown in the aggregate sheet (muted) | ✅ |

### 4.9 `Setting` — 2 toggles (L1141–1152)

| Key | Title | Consumption | Status |
|---|---|---|---|
| `prefer_fhd` | "Ưu tiên 1080p" | The runner reads it via `defaults_get::<bool>("prefer_fhd")` when sorting servers (L833); the app writes it via the toggle → `KrxDefaultsStore` key `{sourceId}.prefer_fhd`; the toggle declares `notification: "Đã thay đổi ưu tiên chất lượng"` → the app sends it to `handle_notification` | ✅ closed loop (UI → store → runner) + notification round-trip |
| `show_intro` | "Hiển thị nút bỏ intro" | The app writes/persists it via the toggle (same store); nothing reads it to change behaviour yet | ◐ UI + persist, no effect yet |
| `clear_cache` (Button, L1157) | "Xoá bộ nhớ đệm nguồn" | Declares `notification: "clear_cache"` — pressing the button → the app calls `runSetting` → `handle_notification("clear_cache")` (the source counts `CACHE_CLEARS` + mirrors defaults) | ✅ demo button → notification |

### 4.10 `Listing` (L1162–1165) & `DeepLinkResult`

| Item | Value | Consumption | Status |
|---|---|---|---|
| `Listing` | `latest` (Mới nhất), `popular` (Phổ biến), `ongoing` (Đang phát), `completed` (Hoàn thành) — all `kind=List` | Listing screen | ✅ 3-column grid, infinite scroll (15/page) |
| `DeepLinkResult::Anime` | `/anime/<key>` | `DeepLinkTarget.Anime` → `playerViewModel.openAnime(stub)` (upgrade Lite→full) | ✅ |
| `DeepLinkResult::Episode` | `/watch/<anime>/<ep>` | `DeepLinkTarget.Episode` → `openAnime(stub, stubEp)`; `PlayerViewModel.loadStreams` swaps the stub episode for the full one by key after `getAnimeUpdate` | ✅ |
| `DeepLinkResult::Listing` | `/list/<id>` | `DeepLinkTarget.Listing` → route `listing/{sourceId}/{listingArg}` (ListingArgCodec) | ✅ |

### 4.11 App-injected — not coming from the source (Aidoku-style, done by komorei itself)

Elements the app inserts into each source's UI/settings itself — the source does NOT need to declare `get_settings`:

| Item | Mechanism | Status |
|---|---|---|
| **Language picker** | Shown when `source.languages.size > 1` (the `SourceSettingsScreen`); writes `{sourceId}.languages` (`HostDefaultValue.StringArray`) via `KrxDefaultsStore` — exactly the key the source reads with `defaults_get::<Vec<String>>("languages")` (Aidoku: `SourceInfoViewController` + `getSelectedLanguages`) | ✅ closed loop (picker → store → defaults_get) |
| **Reset settings** | Deletes every row whose key starts with `{sourceId}.` (`KrxDefaultsStore.deleteAll` → DAO `DELETE ... LIKE '{sourceId}.%'`); touches only that source (name-spacing), anime/watch-history are unrelated | ✅ (Aidoku `removeSettings(from:)`) |
| **Clear cookies** | Expires every cookie of the **domain** `source.baseUrl` (https+http) via `CookieManager` — the same pattern as the host `jsWebviewDeleteCookie`; other sites are untouched | ✅ (Aidoku: Clear Source Cache deletes source URL cookies) |
| **Clear cache** | `repository.clearCachedHome(sourceId)` — clears the source's home layout cache | ✅ |

## 5. Fixture data

### 5.1 `CATALOG` — 22 anime (L73–595)

| # | key | Title | Status | Views | Seasons |
|---|---|---|---|---|---|
| 1 | `solo_leveling_s2` | Solo Leveling: Arise from the Shadow | Ongoing | 2.8M | 2 |
| 2 | `frieren_journey` | Frieren: Pháp Sư Tiễn Táng | Completed | 4.1M | 1 |
| 3 | `dandadan` | Dandadan: Cuộc Chiến Siêu Nhiên | Ongoing | 1.9M | 2 |
| 4 | `jujutsu_kaisen_s2` | Jujutsu Kaisen: Biến Cố Shibuya | Completed | 5.6M | 2 |
| 5 | `demon_slayer_hashira` | Thanh Gươm Diệt Quỷ: Đại Trụ Đặc Huấn | Completed | 3.4M | 3 |
| 6 | `mushoku_tensei_s2` | Thất Nghiệp Chuyển Sinh: Mùa 2 Phần 2 | Completed | 2.2M | 2 |
| 7 | `kimi_no_na_wa` | Your Name (Tên Cậu Là Gì?) | Completed (movie) | 8.9M | 1 |
| 8 | `suzume_no_tojimari` | Khóa Chặt Cửa Nào Suzume | Completed (movie) | 4.7M | 1 |
| 9 | `kaiju_no_8` | Kaiju Số 8 | Completed | 2.5M | 1 |
| 10 | `solo_leveling` | Solo Leveling (Phần 1) | Completed | 3.2M | 2 |
| 11 | `conan` | Thám Tử Lừng Danh Conan | Ongoing | 58M | 1 |
| 12 | `one_piece` | One Piece: Hải Tặc Đại Chiến | Ongoing | 62M | 1 |
| 13 | `attack_on_titan_final` | Attack on Titan: Mùa Cuối | Completed | 8.5M | 1 |
| 14 | `chainsaw_man` | Chainsaw Man: Ma Sư Cưa Máy | Completed | 3.1M | 1 |
| 15 | `spy_family_2` | Spy x Family: Gia Đình Hành Động | Completed | 2.8M | 2 |
| 16 | `death_note` | Death Note: Ghi Chép Tử Thần | Completed | 12M | 1 |
| 17 | `fullmetal_alchemist` | Fullmetal Alchemist: Brotherhood | Completed | 9.5M | 1 |
| 18 | `my_hero_academia_s7` | Học Viện Anh Hùng: Mùa Cuối | Completed | 2.1M | 2 |
| 19 | `dress_up_darling` | 100 Đồ Gái Cosplay | Completed | 1.6M | 1 |
| 20 | `konosuba_s3` | KonoSuba: Phép Thuật Ngon Lành 3 | Completed | 2.4M | 2 |
| 21 | `assassination_classroom` | Lớp Học Ám Sát | Completed | 4M | 1 |
| 22 | `code_geass_r2` | Code Geass: Phản Ứng Của Lelouch | Completed | 5.1M | 2 |

**Featured (5)**: solo_leveling_s2, frieren_journey, dandadan, demon_slayer_hashira, conan.
**Genre coverage**: 12/12 (movies kimi_no_na_wa/suzume = test "Tập 1 • 1080p FHD").

### 5.2 Notable quirks (when tracking progress)

- `generate_episodes` **caps at 64** episodes: Conan (1000) / One Piece (1120) only generate 64 — `episode_count` is still the real number.
- Home `latest_episodes` uses **epoch milliseconds** (`1_700_000_000_000`, L967); `generate_episodes` uses **seconds** (`1_700_000_000`, L686). The app reads timestamps via `Instant.ofEpochMilli`.
- Home `episode_number` is a **bare number** ("10" not "Tập 10"); the app adds the `Tập %1$s` label itself. Movies have no number → fallback `"1"`.
- `get_anime_update(needs_chapters=true)` returns episodes of the **current season key only** (does not pull sibling seasons).
- Search sorts by `SortFilter index`: 0 = rating, 1 = views, otherwise A-Z (title).

## 6. Progress checklist (remaining gaps)

- [x] **Home + Listing**: 7 home components + quality badge + paged grid (`page_size`) + stream/subtitle/intro-outro skip; listing screen (grid + infinite scroll) + navigation from Links/Scroller/"See all" links.
- [x] **Source settings**: `SourceSettingsScreen` renders `get_settings` (both toggles) + persists via `KrxDefaultsStore` (key scoped `{sourceId}.{key}`, DB v4 `MIGRATION_3_4` deletes old rows); `prefer_fhd` is read back by the runner via `defaults_get`; `show_intro` persists but has no runtime effect yet.
- [x] **Per-source home**: `SourceHomeScreen` (home reactive per source, ⋮ menu, Settings/actions).
- [x] **App-injected actions**: language picker (`{sourceId}.languages` — shown when `languages.size > 1`), Reset settings (deletes all `{sourceId}.` rows), Clear cookies (source domain), Clear cache (home cache) — §4.11.
- [x] **NotificationHandler**: a changed setting (toggle/select/stepper/text…) that declares `notification` → the app `repository.handleNotification` → wasm `handle_notification`; pressing the button ("Xoá bộ nhớ đệm nguồn") → sends `"clear_cache"`; the source mirrors into defaults `last_notification` to prove the round-trip — §3, §4.9.
- [x] **Deep link**: `komorei://komorei.example/anime/…` · `/watch/<anime>/<ep>` · `/list/<id>` — `MainActivity` receives the intent (cold + `onNewIntent`) → `DeepLinkManager` → `DeepLinkViewModel` (collect → `DeepLinkResolver` asks each source by host first) → `/anime/`, `/watch/` open the player sheet (`openAnime` Lite stub, `loadStreams` swaps the full episode by key), `/list/` navigates the listing route. Test: `DeepLinkResolverTest` (5 cases) + `DeepLinkViewModelTest` (6 cases).
- [x] **Migration**: the "Migrate source data" button (source Settings screen, confirm dialog) → `SourceMigrationRepository` (injects `AnimeDao` + `AnimeRepository`) collects the distinct anime keys of `anime_library` + `watch_history` for that source, asks via `handle_key_migration` (runner `migrate_anime`/`migrate_episode`, key_kind 0/1; null = treated as identity when the source won't load), then applies the plan in ONE transaction: delete old rows + insert migrated (`REPLACE`, two old keys pointing to one new key merge). Identity source (fake) → every key is unchanged, report 0; the toast shows `migrated/examined`. Test: `SourceMigrationRepositoryTest` (8 cases: wasm round-trip, identity, anime/episode remap, conflict merge, null-safe).
- [x] **DynamicFilters search (per-source)**: the 🔍 button before the chips on each source's home (hidden for the aggregator) → `SourceSearchScreen` YouTube-style (autofocus field + Cancel, sticky filter header, 300ms debounce, skeleton/error/empty/end, infinite scroll). Components split across `ui/components/search/` + `ui/screens/search/` — Aidoku `SearchViewController` + `FilterHeaderView`/`FilterListSheetView`/`Filter*GroupView` port. Test: `SourceSearchViewModelTest` (6 cases, real runner + fake krx).
- [x] **Global search (Discover)**: Search tab (Discover) reimplemented as global search: a search bar + 3 Aidoku-style filters — **content rating** (All/Safe/18+ from the manifest `contentRating`), **language** (union of all sources' `languages`), **source** (multi-select whitelist) — replacing the old genre chips; results merge into a **flat 3-column grid** like per-source search (`DiscoverFilterHeaderRow` reuses `FilterPill`/`FilterBottomSheet`/`SelectFilterGroup`/`MultiSelectFilterGroup` with a hand-built `FilterKind`, no `filters()` call needed). `searchMultiSource` gained `contentRating`/`languages`/`sourceIds` params (filters sources before querying); `SearchViewModel` merges query + genre shortcut + 3 filters into one `combine(...).debounce { 300 }.distinctUntilChanged()` pipeline (`searchDebounceMillis` seam). Test: `SearchViewModelTest` (8 cases).
- [x] **RangeFilter (release year)**: Aidoku-style `RangeFilterRow` (From/To) in the aggregate sheet.
- [x] **Sort UI**: `SortFilterPill` + `SortFilterGroup` (pick index + toggle asc/desc).
- [ ] **Link Url**: "Trang nguồn Komorei" display-only.

---

*Maintained by hand — when a field/func is added to `lib.rs`, update the matching table. Line numbers reference the current commit.*
