# API bề mặt của nguồn Komorei Fake (VI)

> File nguồn: `sources/fake-vi-source/src/lib.rs` (1273 dòng)
> Mục đích: liệt kê **trường / hàm mà source xuất ra** + đối chiếu **mức độ tiêu thụ của app**, để theo dõi tiến độ triển khai. Cập nhật khi sửa source hoặc app.

## Chú thích trạng thái

| Ký hiệu | Ý nghĩa |
|---|---|
| ✅ | App tiêu thụ đầy đủ (có mapping + UI hoạt động) |
| ◐ | Có model/ánh xạ nhưng chưa có UI (hoặc chưa xác nhận) |
| ❌ | Chưa tiêu thụ / display-only / không có chỗ tương ứng trong app |
| ➖ | No-op bên source (không tạo hiệu ứng thật) |

---

## 1. Metadata

| Mục | Giá trị | Dòng |
|---|---|---|
| `SOURCE_ID` | `vi.fake-source` | L19 |
| `PAGE_SIZE` | `15` | L20 |
| Sample HLS | `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` | L22 |
| Sample MP4 720p | `.../Big_Buck_Bunny_720_10s_5MB.mp4` | L23–24 |
| Sample MP4 FHD | `.../BigBuckBunny.mp4` | L25–26 |
| `ALL_GENRES` (12) | Hành Động, Chuyển Sinh, Phiêu Lưu, Harem, Shounen, Lãng Mạn, Siêu Nhiên, Học Đường, Hài Hước, Bí Ẩn, Giả Tưởng, Mecha | L32–45 |
| `languages` (manifest) | `["vi", "en"]` | `res/source.json` L8–10 |

> `languages: ["vi","en"]` — sources list hiện badge **"Đa ngôn ngữ"** (`SourceLabels.kt` khi `languages.size > 1`); đồng thời mở **language picker app-injected** trên màn Settings (xem §4.11 + checklist).

## 2. Các trait đăng ký — `register_source!` (L1241)

| Trait | Trạng thái | Ghi chú app-side |
|---|---|---|
| `Source` | ✅ | Đi qua runner (`registry.call`) |
| `ListingProvider` | ✅ | `get_anime_list` (4 listing) → `AnimeRepository.getListing` → Listing screen (grid + infinite scroll) |
| `Home` | ✅ | `HomeScreen` render đủ 7 component |
| `DynamicFilters` | ◐ | Model + mapping đủ; UI search dùng riêng danh sách genre của app |
| `DynamicSettings` | ✅ | `get_settings` → `SourceSettingsScreen` (toggle + persist qua `KrxDefaultsStore`, key scoped `{sourceId}.`) |
| `DynamicListings` | ✅ | `get_dynamic_listings` → `AnimeRepository.getListings` + chips row Aidoku-style trên Home (`ListingChipsRow`): tap chip đổi content bên dưới sang listing phân trang inline (`HomeListingGrid`) |
| `NotificationHandler` | ✅ | App gọi khi 1 setting đổi có khai `notification` (xem §4.11/§3) qua `handleNotification` → runner `notify` → wasm; nguồn mirror sang defaults `last_notification` (round-trip kiểm chứng được) |
| `DeepLinkHandler` | ❌ | App chưa có routing deep link |
| `MigrationHandler` | ❌ | Source identity; app chưa dùng |
| `SegmentUrlInterceptor` | ✅ | App wrap qua `TransformableHttpDataSource` (`data/remote/`) |
| `SegmentDataInterceptor` | ✅ | Như trên |

## 3. Hàm export (method theo trait)

| Hàm | Signature | Mô tả | Tiêu thụ app |
|---|---|---|---|
| `get_search_anime_list` (L759) | `(query: Option<String>, page: i32, filters: Vec<FilterValue>) -> AnimePageResult` | Search theo title/original_title + lọc + sắp xếp, phân trang 15 | ✅ `AnimeRepository.search` → Search screen |
| `get_anime_update` (L794) | `(anime, needs_details, needs_chapters) -> Anime` | `needs_details` → bản full (`copy_from`); `needs_chapters` → episode của **đúng season key hiện tại** | ✅ `AnimeRepository.getAnimeUpdate` (player + detail) |
| `get_stream_list` (L813) | `(anime, episode) -> Vec<StreamInfo>` | 3 server: `hls`, `mp4_720`, `mp4_fhd`; đọc default `prefer_fhd` để đảo thứ tự | ✅ `getStreamList` → server picker |
| `get_stream` (L842) | `(anime, episode, stream) -> StreamData` | Chọn URL/type theo `stream.key`; kèm headers, subtitle vi, intro/outro | ✅ `getStream` → player (Media3) |
| `get_anime_list` (L895) | `(listing, page) -> AnimePageResult` | `ongoing` / `completed` / `popular` (sort views) / `latest`=all | ✅ `AnimeRepository.getListing` → Listing screen (15/page) |
| `get_home` (L926) | `() -> HomeLayout` | 7 component (xem §5.6) | ✅ `getHome` cache + `HomeScreen` |
| `get_dynamic_filters` (L1093) | `() -> Vec<Filter>` | 5 filter + 1 note (xem §5.7) | ◐ mapping đủ; UI một phần |
| `get_dynamic_settings` (L1140) | `() -> Vec<Setting>` | 2 toggle: `prefer_fhd`, `show_intro` + 1 button `clear_cache` | ✅ màn Settings render + ghi được (persist → runner đọc lại qua `defaults_get`); notification của toggle/button được forward về `handle_notification`; `show_intro` chưa có hiệu ứng runtime |
| `get_dynamic_listings` (L1160) | `() -> Vec<Listing>` | latest / popular / ongoing / completed | ✅ chips `[Trang chủ]+listings` trên Home mỗi source; tap đổi content inline (HomeViewModel: `loadListings`/`selectListing`/`loadListingPage`) |
| `handle_notification` (L1186) | `(key: String)` | App gửi sau mỗi setting change khai `notification` (toggle + button, Aidoku-style): `AnimeRepository.handleNotification` → `runner.notify` → wasm; nguồn mirror key vào defaults `last_notification` + đếm `clear_cache` | ✅ round-trip (settings screen → wasm → defaults, test kiểm chứng) |
| `handle_deep_link` (L1182) | `(url) -> Option<DeepLinkResult>` | `/anime/<key>` · `/watch/<anime>/<ep>` · `/list/<id>` | ❌ |
| `handle_anime_migration` (L1218) | `(key) -> String` | Identity | ❌ |
| `handle_episode_migration` (L1219) | `(anime_key, episode_key) -> String` | Identity | ❌ |
| `intercept_segment_url` (L1225) | `(opt StreamData, url) -> String` | Passthrough | ✅ plumbing sẵn |
| `intercept_segment_data` (L1228) | `(opt StreamData, url, &[u8]) -> Vec<u8>` | Passthrough | ✅ plumbing sẵn |

## 4. Data model (struct chính)

### 4.1 `Anime` — bản **full** (`build_anime`, L603)

| Field runner | Kiểu | Tiêu thụ app (model `model/Anime.kt`) | Trạng thái |
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
| `season_of` | Option<CategoryLink> | `seasonOf` | ◐ luôn `None` từ source |
| `countries` | Vec<CategoryLink> | `countries` | ◐ luôn rỗng |
| `is_featured` | bool | `isFeatured` | ✅ (BigScroller + hero) |
| `views` | i32 | `views` | ✅ (sort "popular"; chưa hiện số) |
| `next_episode_air_info` | Option<String> | `nextEpisodeAirInfo` | ✅ (detail) |
| `quality_tag` | Option<String> | `qualityTag` | ✅ (QualityTagBadge mọi poster/thumb/banner) |
| `seasons` | Vec<AnimeSeason> | `seasons` | ✅ (season picker/chunk) |
| `episodes` | Option<Vec<Episode>> | `episodes` | ✅ (chỉ khi `needs_chapters`) |
| `url` | Option<String> | — | ❌ model app không có trường tương ứng |

### 4.2 `Anime` — bản **lite** (`build_lite`, L653)

`key, source_id, title, original_title, cover, episode_count, current_episode, rating, rating_count, views, release_year, status, quality_tag, genres, is_featured` — phần còn lại `Default`.

> ⚠️ **Lite KHÔNG bị strip hiển thị**: vẫn mang `quality_tag`, `rating`, `current_episode`, `release_year`, `genres`, `status` — nên card home/badge render được ngay không cần upgrade.

### 4.3 `Episode` (`generate_episodes`, L684 — cap 64 tập cho series mega)

| Field | Kiểu | Tiêu thụ app | Trạng thái |
|---|---|---|---|
| `key` | String | `key` | ✅ |
| `episode_number` | **String** | `episodeNumber` (String) | ✅ |
| `title` | Option<String> | | ✅ |
| `thumbnail` | Option<String> | | ✅ |
| `date_uploaded` | Option<i64> (**giây**) | `dateUploaded` | ✅ (trong home `latest` dùng **milli giây**) |
| `duration_seconds` | Option<i64> = 1440 | | ✅ |
| `quality` | Option<String> | | ✅ |
| `url` | Option<String> = None | | ◐ |
| `language` | Some("vi") | | ◐ |
| `locked` | bool = false | | ◐ |

### 4.4 Stream — `StreamInfo` / `StreamData` / `SubtitleInfo` / `RangeLong`

| Struct | Field | Tiêu thụ | Trạng thái |
|---|---|---|---|
| `StreamInfo` | `key, name, quality` | ✅ server picker |
| `StreamData` | `url, stream_type (HLS/MP4), is_content, headers, subtitles` | `is_content=false` → không build media source | ✅ |
| `StreamData` | `intro / outro: Option<RangeLong>` | Skip intro/outro overlay (player) | ✅ |
| `SubtitleInfo` | `url, language, label, headers` | → track subtitle của player | ◐ |
| `RangeLong` | `start_ms, end_ms` | Skip hint `introRange/outroRange` | ✅ |

### 4.5 `AnimePageResult`

| Field | Tiêu thụ | Trạng thái |
|---|---|---|
| `entries: Vec<Anime>` | Phân trang search/listing | ✅ (search + listing) |
| `has_next_page: bool` | Pager tải trang sau | ✅ |

### 4.6 `Link` + `LinkValue` (L937–1007)

| Field/Variant | Tiêu thụ | Trạng thái |
|---|---|---|
| `title, subtitle, image_url` | | ✅ |
| `LinkValue::Anime` | Tap → detail | ✅ |
| `LinkValue::Listing` | Link "Xem toàn bộ…" / genre | ✅ tap → Listing screen (route `listing/{sourceId}/{listingArg}`, listing = URL-safe JSON) |
| `LinkValue::Url` | Link "Trang nguồn" | ❌ display-only |

### 4.7 `HomeComponentValue` — 7 component của `get_home` (L1012–1085)

| Component | Fields | App render | Trạng thái |
|---|---|---|---|
| `ImageScroller` ("Khám Phá") | `links, auto_scroll_interval=4.0, width=800, height=450` | `ImageScrollerRow` | ✅ |
| `BigScroller` ("Nổi Bật") | `entries (full), auto_scroll_interval=5.0` | `BigScrollerRow` | ✅ |
| `Scroller` ("Đang Hot") | `entries (8 lite), listing="hot"` | `ScrollerRow` (AnimeCard rail) | ✅ |
| `AnimeEpisodeList` ("Mới Cập Nhật") | `page_size=Some(4), entries (10), listing="latest"` | `AnimeEpisodeListRow` — `None`=list dọc, `Some(n)`=paged grid | ✅ |
| `AnimeList` ("Phổ Biến Nhất") | `ranking=true, page_size=Some(6), entries (12), listing="popular"` | `AnimeListRow` — paged 2 cột, fixed `pagedCellWidth()` cap 180dp | ✅ |
| `Filters` ("Thể Loại") | `Vec<FilterItem>` (12 genre → `MultiSelect id="genres"`) | `FiltersRow` (chips) | ✅ tap chip → search genre |
| `Links` ("Liên Kết") | 4 link: latest/ongoing/completed/Url | `LinksRow` | ✅ Listing/Anime tap điều hướng; Url display-only |

### 4.8 `Filter` — 5 kiểu (L1094–1132)

| Kiểu | Field | Tiêu thụ | Trạng thái |
|---|---|---|---|
| `TextFilter` | `id="search"`, title/placeholder | Search box app tự quản | ◐ |
| `SortFilter` | `id="sort"`, options Đánh giá/Phổ biến/A-Z | Sort trong `get_search_anime_list` (`sort_entries` L735: rating/views/title) | ◐ model có, UI sort chưa thấy |
| `MultiSelectFilter` | `id="genres"`, `is_genre=true`, `uses_tag_style=true`, 12 options | Genre chip home + `searchMultiSource` qua `buildGenreFilter` | ✅ |
| `SelectFilter` | `id="status"`, options Tất cả/Đang phát/Hoàn thành | `matches_filter` L715 | ◐ |
| `RangeFilter` | `id="year"`, min 1996, max 2025, `decimal=false` | — | ❌ chưa có UI slider |
| `Filter::note` | "Nguồn dữ liệu demo — Komorei Fake (VI)" | — | ◐ |

### 4.9 `Setting` — 2 toggle (L1141–1152)

| Key | Title | Tiêu thụ | Trạng thái |
|---|---|---|---|
| `prefer_fhd` | "Ưu tiên 1080p" | Runner đọc qua `defaults_get::<bool>("prefer_fhd")` khi sắp server (L833); app ghi qua toggle → `KrxDefaultsStore` key `{sourceId}.prefer_fhd`; toggle khai `notification: "Đã thay đổi ưu tiên chất lượng"` → app gửi tới `handle_notification` | ✅ vòng khép kín (UI → store → runner) + notification round-trip |
| `show_intro` | "Hiển thị nút bỏ intro" | App ghi/persist qua toggle (cùng store); chưa có nơi nào đọc để đổi hành vi | ◐ có UI + persist, chưa có hiệu ứng |
| `clear_cache` (Button, L1157) | "Xoá bộ nhớ đệm nguồn" | Khai `notification: "clear_cache"` — bấm button → app gọi `runSetting` → `handle_notification("clear_cache")` (nguồn đếm `CACHE_CLEARS` + mirror defaults) | ✅ demo button → notification |

### 4.10 `Listing` (L1162–1165) & `DeepLinkResult`

| Mục | Giá trị | Tiêu thụ | Trạng thái |
|---|---|---|---|
| `Listing` | `latest` (Mới nhất), `popular` (Phổ biến), `ongoing` (Đang phát), `completed` (Hoàn thành) — tất cả `kind=List` | Listing screen | ✅ grid 3 cột, infinite scroll (15/page) |
| `DeepLinkResult::Anime` | `/anime/<key>` | | ❌ |
| `DeepLinkResult::Episode` | `/watch/<anime>/<ep>` | | ❌ |
| `DeepLinkResult::Listing` | `/list/<id>` | | ❌ |

### 4.11 App-injected — không đến từ source (Aidoku-style, komorei tự làm)

Các phần tử app tự chèn vào UI/settings của từng nguồn — source KHÔNG cần khai báo `get_settings`:

| Mục | Cơ chế | Trạng thái |
|---|---|---|
| **Language picker** | Hiện khi `source.languages.size > 1` (màn `SourceSettingsScreen`); ghi `{sourceId}.languages` (`HostDefaultValue.StringArray`) qua `KrxDefaultsStore` — đúng key nguồn đọc bằng `defaults_get::<Vec<String>>("languages")` (Aidoku: `SourceInfoViewController` + `getSelectedLanguages`) | ✅ vòng khép kín (picker → store → defaults_get) |
| **Reset settings** | Xoá toàn bộ row key bắt đầu `{sourceId}.` (`KrxDefaultsStore.deleteAll` → DAO `DELETE ... LIKE '{sourceId}.%'`); chỉ đụng đúng nguồn (name-spacing), anime/watch-history không liên quan | ✅ (Aidoku `removeSettings(from:)`) |
| **Clear cookies** | Expire mọi cookie của **domain** `source.baseUrl` (https+http) qua `CookieManager` — pattern giống host `jsWebviewDeleteCookie`; các site khác không bị đụng | ✅ (Aidoku: Clear Source Cache xoá cookie source URLs) |
| **Clear cache** | `repository.clearCachedHome(sourceId)` — xoá home layout cache của nguồn | ✅ |

## 5. Dữ liệu fixture

### 5.1 `CATALOG` — 22 anime (L73–595)

| # | key | Title | Trạng thái | Views | Seasons |
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
**Bao phủ genre**: 12/12 (movie kimi_no_na_wa/suzume = test "Tập 1 • 1080p FHD").

### 5.2 Quirk đáng chú ý (khi track tiến độ)

- `generate_episodes` **cap 64** tập: Conan (1000) / One Piece (1120) chỉ tạo 64 — `episode_count` vẫn là số thật.
- Home `latest_episodes` dùng **epoch MILLI giây** (`1_700_000_000_000`, L967); `generate_episodes` dùng **giây** (`1_700_000_000`, L686). App đọc timestamp qua `Instant.ofEpochMilli`.
- `episode_number` của home = **số trần** ("10" không phải "Tập 10"); app tự thêm nhãn `Tập %1$s`. Movie không có số → fallback `"1"`.
- `get_anime_update(needs_chapters=true)` chỉ trả episode của **season key hiện tại** (không kéo season lân cận).
- Search sort theo `SortFilter index`: 0 = rating, 1 = views, khác = A-Z (title).

## 6. Checklist tiến độ (khoảng trống còn lại)

- [x] **Home + Listing**: home 7 component + quality badge + paged grid (`page_size`) + stream/subtitle/intro-outro skip; listing screen (grid + infinite scroll) + điều hướng từ Links/Scroller/Links "Xem toàn bộ".
- [x] **Settings nguồn**: màn `SourceSettingsScreen` render `get_settings` (cả 2 toggle) + persist qua `KrxDefaultsStore` (key scoped `{sourceId}.{key}`, DB v4 `MIGRATION_3_4` xoá row cũ); `prefer_fhd` runner đọc lại qua `defaults_get`; `show_intro` persist nhưng chưa có hiệu ứng runtime.
- [x] **Source home per-source**: `SourceHomeScreen` (home reactive theo từng source, ⋮ menu, vào Settings/actions).
- [x] **App-injected actions**: language picker (`{sourceId}.languages` — hiện khi `languages.size > 1`), Reset settings (xoá mọi `{sourceId}.` rows), Xoá cookie (domain nguồn), Xoá bộ nhớ đệm (home cache) — §4.11.
- [x] **NotificationHandler**: setting đổi (toggle/select/stepper/text…) có khai `notification` → app `repository.handleNotification` → wasm `handle_notification`; button ("Xoá bộ nhớ đệm nguồn") bấm → gửi `"clear_cache"`; nguồn mirror sang defaults `last_notification` để kiểm chứng round-trip — §3, §4.9.
- [ ] **Deep link**: `/anime/…`, `/watch/…`, `/list/…` (source đã có `handle_deep_link`).
- [ ] **Migration**: source identity — app chưa dùng `MigrationHandler`.
- [ ] **RangeFilter (Năm phát hành)**: model đủ, chưa có UI slider search.
- [ ] **Sort UI**: `SortFilter` (Đánh giá/Phổ biến/A-Z) chạy được trong search nhưng chưa có giao diện chọn.
- [ ] **Link Url**: "Trang nguồn Komorei" display-only.

---

*File duy trì thủ công — khi thêm field/func vào `lib.rs`, cập nhật bảng tương ứng. Số dòng tham chiếu theo commit hiện tại.*