## [1.1.3](https://github.com/tachibana-shin/komorei-app/compare/v1.1.2...v1.1.3) (2026-09-27)


### Bug Fixes

* **runner:** build wasmi with the portable dispatch backend ([0e4cde1](https://github.com/tachibana-shin/komorei-app/commit/0e4cde163c261dba22f863cf3c8554a3804a25f4)), closes [#00](https://github.com/tachibana-shin/komorei-app/issues/00) [#01](https://github.com/tachibana-shin/komorei-app/issues/01) [#02](https://github.com/tachibana-shin/komorei-app/issues/02) [#03](https://github.com/tachibana-shin/komorei-app/issues/03)

## [1.1.2](https://github.com/tachibana-shin/komorei-app/compare/v1.1.1...v1.1.2) (2026-09-27)


### Bug Fixes

* **runner:** bound the wasm call depth so a runaway source cannot kill the app ([e6b9902](https://github.com/tachibana-shin/komorei-app/commit/e6b99027379acb6aee5eb6c458567ee5f204c045))

## [1.1.1](https://github.com/tachibana-shin/komorei-app/compare/v1.1.0...v1.1.1) (2026-09-26)


### Bug Fixes

* **test:** pass the animevietsub fixtures path in, and load it eagerly ([709cd83](https://github.com/tachibana-shin/komorei-app/commit/709cd83bf78e687764766e766845723e9c4da2dd))

# [1.1.0](https://github.com/tachibana-shin/komorei-app/compare/v1.0.1...v1.1.0) (2026-09-26)


### Features

* forward streamed home results, and carry `Anime.extra` through the app ([55ec408](https://github.com/tachibana-shin/komorei-app/commit/55ec408728cf9b3ea4ecb3c243e4c2f0191317b0))

## [1.0.1](https://github.com/tachibana-shin/komorei-app/compare/v1.0.0...v1.0.1) (2026-09-26)


### Bug Fixes

* make the OTA version gate testable and the challenge watcher deterministic ([7b6074e](https://github.com/tachibana-shin/komorei-app/commit/7b6074e5f02b783c69ee417dae94ab3666112252))

# 1.0.0 (2026-09-25)


### Bug Fixes

* **gradle:** stage uniffi bindings before patching them ([f257a69](https://github.com/tachibana-shin/komorei-app/commit/f257a69a5258f5525e621ebb525526dabfa4136b))
* harden empty & error states, align skeleton sizes ([c1d50e1](https://github.com/tachibana-shin/komorei-app/commit/c1d50e113d5c2ee4a757c6d36906deb72c2609d5))
* **home:** share fixed paged cell width across AnimeList and AnimeEpisodeList ([09671eb](https://github.com/tachibana-shin/komorei-app/commit/09671eb3c4e3028df9964399a353489b921ee038))
* move hardcoded strings to resources, fix route slash bug, add virtual seasons (max 50 eps) ([1acb675](https://github.com/tachibana-shin/komorei-app/commit/1acb67501cf12503febd45600928fd8e51645e39))
* **player:** defer auto-resume seek until player is ready ([c46b089](https://github.com/tachibana-shin/komorei-app/commit/c46b089bc34e41887875aaecbdd5fdaff531d214))
* replace dead test stream URLs with working sample sources ([397f126](https://github.com/tachibana-shin/komorei-app/commit/397f126ff3081b8600c1db5477dcc4a5812a9327))
* **repos:** resolve relative manifest URLs against the repo base ([b2d67d9](https://github.com/tachibana-shin/komorei-app/commit/b2d67d988ba9e27a4845d6ee387482292a662b1e))
* resolve compilation errors in SourceBrowserScreen and SourceSettingsScreen ([da0d5a9](https://github.com/tachibana-shin/komorei-app/commit/da0d5a975bec126e95a5c785e647d842f22f68f6))
* **sdk:** add missing jsoup/jna version catalog entries ([0f834bb](https://github.com/tachibana-shin/komorei-app/commit/0f834bb6b88abbdb82eeb649d8dd7b704834fbba))
* **sdk:** Element::data() returns script content, not the data attribute ([2eb440b](https://github.com/tachibana-shin/komorei-app/commit/2eb440bddeb99a5ee152fecec9a3808b53c925ee))
* SearchHistoryStore supersedes typing-pause prefix partials ([4d52ad2](https://github.com/tachibana-shin/komorei-app/commit/4d52ad237b0e8b8d5beac48da4796af66a839188))
* **search:** persist search across tabs + add Discover all-filters sheet ([3fdd91d](https://github.com/tachibana-shin/komorei-app/commit/3fdd91dbbe97fddf60ee9ac94d91c78fc1a084ae))
* source settings black screen — make it a full-screen page, not a bottom sheet ([66de38f](https://github.com/tachibana-shin/komorei-app/commit/66de38fbc0d5f373815bd2ba25ee42a3f9a87b09))
* **ui:** bottom sheets leave room for the system navigation bar ([c65455d](https://github.com/tachibana-shin/komorei-app/commit/c65455dca7810f73851e54852af327c4acc1d27e))
* **ui:** improve player text alignment and seek bar segment visibility ([fb889d7](https://github.com/tachibana-shin/komorei-app/commit/fb889d7c5699b2df8ef5ced9a78e40bd685bf5b1))
* **ui:** make the phone bottom bar a fixed row (no horizontal scroll) ([77b6044](https://github.com/tachibana-shin/komorei-app/commit/77b6044680d7ffc6ba8d46b95d51d919e48a5cfe))


### Features

* add 403/JS challenge handling via headless WebView + search history replace genre grid ([6686bed](https://github.com/tachibana-shin/komorei-app/commit/6686bedc9439577af63e970fa8fb2049a3f0bb21))
* Aidoku-style DynamicFilters per-source search UI ([45fc81e](https://github.com/tachibana-shin/komorei-app/commit/45fc81eec1f9b930b78034d9bdca2cf8fd331d6b))
* Aidoku-style global search for Khám Phá tab (rating/language/sources filters) ([ed8a140](https://github.com/tachibana-shin/komorei-app/commit/ed8a1404752bfafbb1a17872e1fd0df34ab03600))
* **fake-source:** .krx fake VI source crate + full-stack runner proof ([7f26bea](https://github.com/tachibana-shin/komorei-app/commit/7f26bea3a7f0218f50506bada4626dbbf3b31f2b))
* **filters:** full filter model with Room + Moshi persistence ([d65a7bc](https://github.com/tachibana-shin/komorei-app/commit/d65a7bc0cee8fd7b3ba03e8df8c49ec10533bd99))
* **home:** increase cell width and define explicit page size for fake source ([986631e](https://github.com/tachibana-shin/komorei-app/commit/986631e912b3f6e28ff6a1da2be751a870950de5))
* implement DeepLinkHandler — deep links open player or listing ([f5b3f3d](https://github.com/tachibana-shin/komorei-app/commit/f5b3f3d86f66a05ccf62be67a8189031410dfee3))
* implement Room database and watch history tracking ([ab53bae](https://github.com/tachibana-shin/komorei-app/commit/ab53bae7d113c2a78dcf9a28a33f3268460e12e7))
* integrate Hilt for dependency injection ([0ed6d72](https://github.com/tachibana-shin/komorei-app/commit/0ed6d722e2a90c34f6c3a16d3118aaa1350722ba))
* **listing:** paged listing screen wired from home see-all links ([a0b1e57](https://github.com/tachibana-shin/komorei-app/commit/a0b1e5784a98e42f46758fbf4849f3cb39a97ea8))
* make filter bottom sheet apply changes immediately without Apply/Cancel buttons ([046e63e](https://github.com/tachibana-shin/komorei-app/commit/046e63e654129bfc1aa633a7b46dc43da918584e))
* MigrationHandler — re-key stored library + watch history via handle_key_migration ([47a9075](https://github.com/tachibana-shin/komorei-app/commit/47a90757d35c58e7e7bfdc1f40ff877ec3aa0fc9))
* **player:** enhance state management and implement mini player ([7b6c51a](https://github.com/tachibana-shin/komorei-app/commit/7b6c51aa04fdf92ab42a24156b724a21b286ef54))
* **player:** enhance video player UI/UX with YouTube-style controls ([954cfcb](https://github.com/tachibana-shin/komorei-app/commit/954cfcb3ed56ca6ce8a88d421b0c9a9dfb1d94d7))
* **player:** implement intro/outro skipping and auto-play next episode ([1ef8d6b](https://github.com/tachibana-shin/komorei-app/commit/1ef8d6be9a09ef3938099a0b86b67ee002804e05))
* **player:** implement PiP-style floating mini player and momentum transitions ([586dd8f](https://github.com/tachibana-shin/komorei-app/commit/586dd8f2a5558cf53bb956e9dd4387e14c2ebdf7))
* **player:** implement session persistence and back navigation ([6f21fe2](https://github.com/tachibana-shin/komorei-app/commit/6f21fe2a2f6d5b4549ac0e958c98fb9ba68bf8bf))
* **player:** implement watch history and auto-resume ([bdbadec](https://github.com/tachibana-shin/komorei-app/commit/bdbadec988545fc70b40e543554671e3a8a7b9fe))
* **player:** improve track selection layout and subtitle syncing ([971ce59](https://github.com/tachibana-shin/komorei-app/commit/971ce59ad3dab328eba7449d83efda3b1b286b09))
* **player:** overhaul UI and implement advanced playback controls ([220a3e2](https://github.com/tachibana-shin/komorei-app/commit/220a3e29552446a10c5f7fb9303b533c54c7bc76))
* **player:** overhaul zoom gestures and scroll state management ([245111f](https://github.com/tachibana-shin/komorei-app/commit/245111fd9b3e42f24361de823a4d86a9b18145f6))
* **player:** refactor quality selection and replace aspect ratio with pinch-zoom ([525b9a5](https://github.com/tachibana-shin/komorei-app/commit/525b9a55f40d82bfd4f8986a99592159aedb9e3e))
* **player:** refine zoom persistence and gesture handling during fullscreen transitions ([22b913d](https://github.com/tachibana-shin/komorei-app/commit/22b913dcdcf0827c6c6d62822ee0d2866a7fb296))
* **player:** refine zoom persistence and gesture handling during fullscreen transitions ([080fbd8](https://github.com/tachibana-shin/komorei-app/commit/080fbd82abbc12ea002fe05389e47d2a1b571481))
* pull to refresh and responsive screen ([0540cd7](https://github.com/tachibana-shin/komorei-app/commit/0540cd748eca4a69bea7a16a4021160be8587094))
* refactor anime detail view and state management ([75dfadb](https://github.com/tachibana-shin/komorei-app/commit/75dfadbb3e0ac2863168b12c9823248039fdb57e))
* refactor anime model and integrate Jetpack Navigation ([84cdeb6](https://github.com/tachibana-shin/komorei-app/commit/84cdeb6ca6db589ec8a3402eb570fee0629bae40))
* scaffold base project architecture ([2fc5772](https://github.com/tachibana-shin/komorei-app/commit/2fc57727813a3975ef1a579c01a6e70e0f88693a))
* **sdk:** rework JS-challenge (403) bypass with headless + visible browser ([d6973af](https://github.com/tachibana-shin/komorei-app/commit/d6973af325ff0aed83152008568d370ee437a2eb))
* **sdk:** runner-backed repository and dynamic home layout integration ([18f145e](https://github.com/tachibana-shin/komorei-app/commit/18f145e540714544d4142ea8dd64c61df1c42a68))
* **sdk:** WebView-backed JS module and vendored Rust runner ([5b1d3a6](https://github.com/tachibana-shin/komorei-app/commit/5b1d3a6bfb8db0c1a5e90cccbf78667cd9a197d0))
* search results display per-source horizontal sections with icons ([c0df77c](https://github.com/tachibana-shin/komorei-app/commit/c0df77ce660c6b6346c0b87e122ac04766f97c06))
* **sources:** add OPhim source collection (vi.ophim) ([56c3d64](https://github.com/tachibana-shin/komorei-app/commit/56c3d6414425490da92d656beca3e04df90802fa))
* streaming per-source search results with live sections ([5256c70](https://github.com/tachibana-shin/komorei-app/commit/5256c70bea0e39afcadef1232d5bceef695a1a76))
* support raw media content and refactor stream metadata ([ce7cdd0](https://github.com/tachibana-shin/komorei-app/commit/ce7cdd08faef376d62c09de18a78da6d5efbb735))
* **ui:** add Lịch chiếu, Xếp hạng, Thông báo and Cài đặt tabs ([a7ef7ce](https://github.com/tachibana-shin/komorei-app/commit/a7ef7ce74c169151f4ff25ef2bc5a96f0e2a76d5))
* **ui:** add quality tag badge to anime cards ([9b383a0](https://github.com/tachibana-shin/komorei-app/commit/9b383a0886ed78514c6d0d3e63f1c236b5672aa4))
* **ui:** swap Search and Sources tab positions ([ef99ad6](https://github.com/tachibana-shin/komorei-app/commit/ef99ad618c207785c803e8bd23a43ccb7f112a99))
* **ui:** update intro/outro highlights and add screenshot test ([a8249ff](https://github.com/tachibana-shin/komorei-app/commit/a8249ff1bf3504236f3fff5fb85bba090725aba1))


### Reverts

* **ui:** drop the Lịch chiếu and Xếp hạng tabs ([d109ccf](https://github.com/tachibana-shin/komorei-app/commit/d109ccff8ac7f079d104f7465bde60f7aedf2136))
