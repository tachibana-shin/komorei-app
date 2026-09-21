# Bộ sưu tập nguồn Komorei

Bộ sưu tập nguồn nội dung cho app **Komorei**, theo kiểu `aidoku-community/sources`:
mỗi nguồn là một crate Rust `no_std` biên dịch sang `wasm32-unknown-unknown` và
đóng gói thành một tệp `.krx`.

```
sources/
├── fake-vi-source/   # nguồn MẪU: dữ liệu giả hoàn toàn (chạy được không cần mạng)
├── ophim/            # OPhim API (classic ophim1.com + fork phẳng phimapi.com)
└── README.md
```

## Cấu trúc một nguồn

```
sources/tên-nguồn/
├── Cargo.toml            # crate-type = ["cdylib"]; deps: komorei (+ serde khi cần JSON)
├── .cargo/config.toml    # build target mặc định = wasm32-unknown-unknown
├── res/
│   ├── source.json       # manifest (info.id/name/version/url/languages/contentRating)
│   └── icon.png          # icon nguồn (hiện thị trong tab Nguồn)
├── src/lib.rs            # triển khai các trait của komorei-sdk + register_source!
└── package.krx           # build artifact, không commit (xem .gitignore)
```

`source.json` nằm trong gói với layout `Payload/{main.wasm, source.json, icon.png}`
(đọc bởi `KrxManager.readInfo`/`extractMainWasm` trong app).

## Build & đóng gói `.krx`

```sh
cd sources/ophim

# 1) compile wasm
cargo build --release --target wasm32-unknown-unknown

# 2) ráp gói (layout Payload/ như komorei-sdk/crates/cli/src/commands/package.rs)
rm -rf /tmp/krx && mkdir -p /tmp/krx/Payload
cp target/wasm32-unknown-unknown/release/ophim_source.wasm /tmp/krx/Payload/main.wasm
cp res/source.json res/icon.png /tmp/krx/Payload/
(cd /tmp/krx && zip -r /home/shin/komorei-app/sources/ophim/package.krx Payload)
```

> Lưu ý: `package.krx` và `target/` bị `.gitignore` loại — chúng là artifact
> tái tạo được. `fake-vi-source` đi theo quy ước ngược lại một chút: `target` là
> symlink cũ trỏ ổ `/mnt/komorei-data` đã chết (di sản, không sửa).

## Đăng ký nguồn vào app

- **Bundled**: copy `package.krx` vào `app/src/main/assets/sources/` — được
  `KrxSourceRegistry` nạp lúc khởi động (không thể gỡ cài đặt).
- **Người dùng cài**: `.krx` nhập qua tab Nguồn → `filesDir/sources/`.
- **Test JVM**: thêm một system property `komorei.test.<tên>` trong
  `app/build.gradle.kts` (xem `komorei.test.ophimKrx`) và viết test kiểu
  `OphimSourceRunnerIntegrationTest` — chạy WASM thật qua runner cdylib +
  `KrxHostImpl` thật.

## Checklist thêm nguồn mới

1. Khai báo id theo key pattern `[A-Za-z0-9.\-]+` (bắt buộc cho `installKrx`).
2. Triển khai `Source` (search/animeUpdate/streams) trước, rồi ghép thêm các
   trait còn lại (Listing/Home/Filters/Settings/DeepLink/Notification/Migration).
3. Build wasm → đóng gói `.krx` theo mục trên.
4. Viết test integration JVM dựa trên fixture local (không phụ thuộc domain
   live — xem `OphimSourceRunnerIntegrationTest`).
5. Cập nhật `res/source.json` + README nguồn.