# Nguồn OPhim (`vi.ophim`)

Khai thác **API OPhim** (`https://ophim1.com`) và các clone cùng họ
(`https://phimapi.com` …) cho app Komorei. Xử lý được **cả hai dạng envelope**:
cổ điển `{ status, data: { items | item, params } }` và fork phẳng
`{ status, items | movie }`.

> ⚠️ Các domain OPhim chết và tái sinh liên tục. Thời điểm viết (09/2026),
> `ophim1.com` và mọi mirror classic đều mất kết nối; `phimapi.com` còn sống
> nhưng là **fork cụt** — search/list/detail metadata OK, **không trả
> `episodes[].server_data[].link_m3u8`** (không phát được). Vì vậy base URL
> phải cài đặt được: mặc định `https://ophim1.com`, đổi ở **Cài đặt nguồn →
> “Địa chỉ API OPhim”** (setting `base_url`, đọc lại mỗi request qua
> `defaults_get`, không cần cache).

## Mô hình dữ liệu → Komorei

| Komorei | OPhim |
|---|---|
| `Anime` (lite) | item trong list/search |
| `Anime` (full) | detail `GET {base}/phim/{slug}` |
| `AnimeSeason` | **một server phát** trên detail (`episodes[].server_name`) |
| `Episode` | entry trong `server_data` — `key = slug` (vd `tap-1`) |

- **Season = server phát.** `AnimeSeason.anime_id` được encode thành
  `"{slug}|{server_name}"`. App gọi `get_anime_update` với key đó (qua
  `fetchEpisodesForSeason`), source bóc phần sau `|` để trả đúng tập của server
  đã chọn. Server sắp theo số tập giảm dần → server lớn nhất (thường `OPhim`)
  là mặc định.
- **`get_stream_list`** trả 1 `StreamInfo`/server (lấy từ `seasons` vừa nhận,
  **không** tốn request thêm).
- **`get_stream`** fetch lại `{base}/phim/{slug}`, tìm nhóm theo
  `stream.key` (server) + entry theo `episode.key`; nếu server yêu cầu không có
  tập đó (app auto-resolve server đầu tiên) thì **fallback sang group đầu tiên
  chứa tập**. Trả `StreamData` với `is_content: false` (media URI thường) +
  header `Referer: {base}/`.
- **Danh sách/phân trang**: `danh-sach` (list) và `tim-kiem` (search) cùng đọc
  `data.items`/`items` + `params.pagination`; khi fork không trả pagination thì
  `has_next` fallback theo ngưỡng 24 item/trang.
- **Filter**: genre/country name → slug OPhim qua 2 bảng `GENRES`/`COUNTRIES`
  (slugify đơn giản khi lạ); type `series|single`; year; sort
  `modified.time|year|name`.

## URLs

| Hành động | URL |
|---|---|
| Home (3 rail song song) | `GET {base}/danh-sach/{phim-moi-cap-nhat,phim-bo,phim-le}?page=1` |
| Browse (không keyword) | `GET {base}/danh-sach/phim-moi-cap-nhat?page=N&filters…` |
| Search (có keyword) | `GET {base}/tim-kiem?page=N&keyword=…&filters…` |
| Detail | `GET {base}/phim/{slug}` |
| Deep link | `/phim/{slug}` → Anime · `/xem-phim/{slug}/{ep-slug}` → Episode |

## Build, đóng gói & kiểm thử

```sh
# wasm + package.krx (xem thêm sources/README.md)
cargo build --release --target wasm32-unknown-unknown

# test unit thuần trên host (không cần komorei-test-runner):
# --target x86_64-unknown-linux-gnu đề phòng build.target wasm trong config.
cargo test --target x86_64-unknown-linux-gnu

# test integration JVM chạy WASM thật qua runner + host thật,
# đối đầu fixture classic-OPHIM local (không cần mạng):
./gradlew :app:testDebugUnitTest \
  --tests "git.shin.komorei.sdk.OphimSourceRunnerIntegrationTest"
```

Bộ 16 test phủ: search (browse/search, cả 2 envelope), home rails, detail +
seasons-per-server, chapters scoped theo key `{slug}|{server}`, stream list/resolve,
fallback server + bail "không có tập", filters, settings (`base_url`), listings,
deep links, notification, migration identity. (Thêm 9 test unit Rust trên host.)

## Ghi chú triển khai

- `MigrationHandler` giữ nguyên identity (`OPhim` không đổi id qua phiên bản).
- `parse_isodate_millis` (Hinnant `days_from_civil`) → epoch millis từ
  `modified.time` (app đọc `Instant.ofEpochMilli`).
- `strip_html` bóc tag + một số entity HTML trong `content` thành description.