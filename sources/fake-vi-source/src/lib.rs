#![no_std]
extern crate alloc;

use alloc::{format, string::String, vec, vec::Vec};
use komorei::{
	imports::defaults::defaults_get,
	prelude::*,
	Anime, AnimePageResult, AnimeSeason, AnimeStatus, AnimeWithEpisode, CategoryLink,
	DeepLinkHandler, DeepLinkResult, DynamicFilters, DynamicListings, DynamicSettings, Episode,
	Filter, FilterValue, Home, HomeComponent, HomeComponentValue, HomeLayout, Link, LinkValue,
	Listing, ListingKind, ListingProvider, MigrationHandler, MultiSelectFilter,
	NotificationHandler, RangeFilter, RangeLong, Result, SelectFilter, Setting, SortFilter,
	StreamData, StreamInfo, StreamType, SubtitleInfo, TextFilter, ToggleSetting, Source,
	SegmentDataInterceptor, SegmentUrlInterceptor,
};

use core::cmp::Ordering;

const SOURCE_ID: &str = "vi.fake-source";
const PAGE_SIZE: i32 = 15;

const SAMPLE_HLS: &str = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";
const SAMPLE_MP4_720: &str =
	"https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_5MB.mp4";
const SAMPLE_MP4_FHD: &str =
	"https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";

// ── Catalog ─────────────────────────────────────────────────────────────────

struct Entry {
	key: &'static str,
	title: &'static str,
	original_title: &'static str,
	cover: &'static str,
	banner: &'static str,
	description: &'static str,
	episode_count: i32,
	current_episode: &'static str,
	rating: f32,
	rating_count: i32,
	status: AnimeStatus,
	release_year: &'static str,
	genres: &'static [&'static str],
	author: &'static str,
	studio: &'static str,
	views: i32,
	featured: bool,
	next_air: Option<&'static str>,
	quality_tag: &'static str,
	seasons: &'static [(&'static str, &'static str)],
	source_url: &'static str,
}

const CATALOG: &[Entry] = &[
	Entry {
		key: "solo_leveling_s2",
		title: "Solo Leveling: Arise from the Shadow",
		original_title: "Ore dake Level Up na Ken Season 2",
		cover: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600",
		banner: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1200",
		description: "Sung Jin-woo tiếp tục hành trình thức tỉnh sức mạnh Chúa Tể Bóng Tối, đối mặt với các Thợ Săn Cấp Quốc Gia và giải cứu thế giới khỏi hiểm họa hầm ngục.",
		episode_count: 13,
		current_episode: "Tập 10/13",
		rating: 4.95,
		rating_count: 12500,
		status: AnimeStatus::Ongoing,
		release_year: "2025",
		genres: &["Hành Động", "Siêu Nhiên"],
		author: "Chugong",
		studio: "A-1 Pictures",
		views: 2_800_000,
		featured: true,
		next_air: Some("Tập 11 phát sóng lúc 22:30 Thứ Bảy ngày 12/10"),
		quality_tag: "FHD",
		seasons: &[("solo_leveling", "Phần 1: Thức Tỉnh"), ("solo_leveling_s2", "Phần 2: Arise")],
		source_url: "https://komorei.example/anime/solo-leveling-s2",
	},
	Entry {
		key: "frieren_journey",
		title: "Frieren: Pháp Sư Tiễn Táng",
		original_title: "Sousou no Frieren",
		cover: "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=600",
		banner: "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=1200",
		description: "Hành trình sâu lắng của pháp sư Elf Frieren sau khi Đội Anh Hùng đánh bại Quỷ Vương, đi tìm ý nghĩa của cuộc sống và sự hữu hạn của thời gian.",
		episode_count: 28,
		current_episode: "Tập 28/28 End",
		rating: 4.98,
		rating_count: 8500,
		status: AnimeStatus::Completed,
		release_year: "2024",
		genres: &["Phiêu Lưu", "Giả Tưởng"],
		author: "Kanehito Yamada",
		studio: "Madhouse",
		views: 4_100_000,
		featured: true,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[("frieren_journey", "Phần 1: Hành Trình Mới")],
		source_url: "https://komorei.example/anime/frieren",
	},
	Entry {
		key: "dandadan",
		title: "Dandadan: Cuộc Chiến Siêu Nhiên",
		original_title: "Dandadan",
		cover: "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600",
		banner: "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200",
		description: "Momo Ayase tin vào ma quỷ nhưng không tin người ngoài hành tinh. Okarun ngược lại. Một vụ cá cược định mệnh đưa họ vào thế giới hỗn loạn của quái vật và thế lực kỳ bí.",
		episode_count: 24,
		current_episode: "Tập 12/24",
		rating: 4.91,
		rating_count: 5200,
		status: AnimeStatus::Ongoing,
		release_year: "2024",
		genres: &["Hành Động", "Siêu Nhiên"],
		author: "Yukinobu Tatsu",
		studio: "Science SARU",
		views: 1_900_000,
		featured: true,
		next_air: Some("Tập tiếp theo (Tập 13) phát lúc 23:00 Thứ Năm hàng tuần"),
		quality_tag: "FHD",
		seasons: &[("dandadan", "Phần 1: Chạm Trán"), ("dandadan_s2", "Phần 2: Quỷ Ác Tà")],
		source_url: "https://komorei.example/anime/dandadan",
	},
	Entry {
		key: "jujutsu_kaisen_s2",
		title: "Jujutsu Kaisen: Biến Cố Shibuya",
		original_title: "Jujutsu Kaisen 2nd Season",
		cover: "https://images.unsplash.com/photo-1563089145-599997674d42?w=600",
		banner: "https://images.unsplash.com/photo-1563089145-599997674d42?w=1200",
		description: "Cuộc chiến khốc liệt nhất lịch sử Chú Thuật Sư tại ngã tư Shibuya khi Gojo Satoru bị phong ấn trong Ngục Môn Cương.",
		episode_count: 23,
		current_episode: "Tập 23/23 End",
		rating: 4.96,
		rating_count: 15000,
		status: AnimeStatus::Completed,
		release_year: "2023",
		genres: &["Hành Động", "Shounen"],
		author: "Gege Akutami",
		studio: "MAPPA",
		views: 5_600_000,
		featured: false,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[
			("jujutsu_kaisen", "Phần 1: Chú Thuật"),
			("jujutsu_kaisen_s2", "Phần 2: Sự Cố Shibuya"),
		],
		source_url: "https://komorei.example/anime/jjk-s2",
	},
	Entry {
		key: "demon_slayer_hashira",
		title: "Thanh Gươm Diệt Quỷ: Đại Trụ Đặc Huấn",
		original_title: "Kimetsu no Yaiba: Hashira Geiko-hen",
		cover: "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600",
		banner: "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=1200",
		description: "Tanjiro và Sát Quỷ Đội bắt đầu đợt tập huấn khắc nghiệt dưới sự hướng dẫn của các Trụ Cột trước trận quyết chiến tại Vô Hạn Thành.",
		episode_count: 8,
		current_episode: "Tập 8/8 End",
		rating: 4.88,
		rating_count: 9800,
		status: AnimeStatus::Completed,
		release_year: "2024",
		genres: &["Hành Động", "Shounen"],
		author: "Koyoharu Gotouge",
		studio: "ufotable",
		views: 3_400_000,
		featured: true,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[
			("demon_slayer_s1", "Phần 1: Phố Đèn Đỏ"),
			("demon_slayer_s2", "Phần 2: Làng Thợ Rèn"),
			("demon_slayer_hashira", "Phần 3: Đại Trụ Đặc Huấn"),
		],
		source_url: "https://komorei.example/anime/kimetsu",
	},
	Entry {
		key: "mushoku_tensei_s2",
		title: "Thất Nghiệp Chuyển Sinh: Mùa 2 Phần 2",
		original_title: "Mushoku Tensei II: Isekai Ittara Honki Dasu Part 2",
		cover: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
		banner: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200",
		description: "Rudeus Greyrat đến Mê Cung Rapan để giải cứu mẹ Zenith cùng cha Paul và sư phục Roxy, trải qua thử thách đầy cảm xúc.",
		episode_count: 12,
		current_episode: "Tập 12/12 End",
		rating: 4.93,
		rating_count: 11000,
		status: AnimeStatus::Completed,
		release_year: "2024",
		genres: &["Chuyển Sinh", "Phiêu Lưu"],
		author: "Rifujin na Magonote",
		studio: "Studio Bind",
		views: 2_200_000,
		featured: false,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[
			("mushoku_tensei", "Phần 1: Học Viện"),
			("mushoku_tensei_s2", "Phần 2: Mê Cung Rapan"),
		],
		source_url: "https://komorei.example/anime/mushoku-tensei",
	},
	Entry {
		key: "kimi_no_na_wa",
		title: "Your Name (Tên Cậu Là Gì?)",
		original_title: "Kimi no Na wa.",
		cover: "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600",
		banner: "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200",
		description: "Kiệt tác điện ảnh của đạo diễn Makoto Shinkai kể về cuộc hoán đổi thân xác kỳ diệu giữa Mitsuha ở vùng quê Itomori và Taki ở Tokyo náo nhiệt.",
		episode_count: 1,
		current_episode: "Bản Chiếu Rạp FHD",
		rating: 4.99,
		rating_count: 25000,
		status: AnimeStatus::Completed,
		release_year: "2016",
		genres: &["Tình Cảm", "Siêu Nhiên"],
		author: "Makoto Shinkai",
		studio: "CoMix Wave Films",
		views: 8_900_000,
		featured: false,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[("kimi_no_na_wa", "Bản Chiếu Rạp")],
		source_url: "https://komorei.example/anime/kimi-no-na-wa",
	},
	Entry {
		key: "suzume_no_tojimari",
		title: "Khóa Chặt Cửa Nào Suzume",
		original_title: "Suzume no Tojimari",
		cover: "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600",
		banner: "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=1200",
		description: "Cô gái 17 tuổi Suzume tình cờ gặp một thanh niên bí ẩn tìm kiếm một cánh cửa. Hai người cùng lên đường khóa những cánh cửa tai họa khắp Nhật Bản.",
		episode_count: 1,
		current_episode: "Bản Chiếu Rạp FHD",
		rating: 4.92,
		rating_count: 18000,
		status: AnimeStatus::Completed,
		release_year: "2022",
		genres: &["Phiêu Lưu", "Siêu Nhiên"],
		author: "Makoto Shinkai",
		studio: "CoMix Wave Films",
		views: 4_700_000,
		featured: false,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[("suzume_no_tojimari", "Bản Chiếu Rạp")],
		source_url: "https://komorei.example/anime/suzume",
	},
	Entry {
		key: "kaiju_no_8",
		title: "Kaiju Số 8",
		original_title: "Kaijuu 8-gou",
		cover: "https://images.unsplash.com/photo-1563089145-599997674d42?w=600",
		banner: "https://images.unsplash.com/photo-1563089145-599997674d42?w=1200",
		description: "Kafka Hibino 32 tuổi biến thành quái thú Kaiju Số 8 nhưng vẫn nuôi ước mơ gia nhập Lực Lượng Phòng Vệ Nhật Bản.",
		episode_count: 12,
		current_episode: "Tập 12/12 End",
		rating: 4.87,
		rating_count: 7600,
		status: AnimeStatus::Completed,
		release_year: "2024",
		genres: &["Hành Động", "Sci-Fi"],
		author: "Naoya Matsumoto",
		studio: "Production I.G",
		views: 2_500_000,
		featured: false,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[("kaiju_no_8", "Phần 1: Thức Tỉnh")],
		source_url: "https://komorei.example/anime/kaiju-no-8",
	},
	Entry {
		key: "solo_leveling",
		title: "Solo Leveling (Phần 1)",
		original_title: "Ore dake Level Up na Ken",
		cover: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600",
		banner: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1200",
		description: "Hành trình từ thợ săn yếu nhất đến đỉnh cao.",
		episode_count: 12,
		current_episode: "Full 12/12",
		rating: 4.9,
		rating_count: 8000,
		status: AnimeStatus::Completed,
		release_year: "2024",
		genres: &["Hành Động"],
		author: "Chugong",
		studio: "A-1 Pictures",
		views: 3_200_000,
		featured: false,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[("solo_leveling", "Phần 1: Thức Tỉnh"), ("solo_leveling_s2", "Phần 2: Arise")],
		source_url: "https://komorei.example/anime/solo-leveling",
	},
	Entry {
		key: "conan",
		title: "Thám Tử Lừng Danh Conan",
		original_title: "Detective Conan",
		cover: "https://images.unsplash.com/photo-1528459584353-5297db1a9c01?w=600",
		banner: "https://images.unsplash.com/photo-1528459584353-5297db1a9c01?w=1200",
		description: "Kudo Shinichi bị teo nhỏ thành cậu học sinh tiểu học sau khi trúng độc của tổ chức Áo Đen, mang tên Conan Edogawa tiếp tục phá án và truy tìm tổ chức bí ẩn.",
		episode_count: 1000,
		current_episode: "Tập 973/1000",
		rating: 4.85,
		rating_count: 25000,
		status: AnimeStatus::Ongoing,
		release_year: "1996",
		genres: &["Trinh Thám", "Shounen"],
		author: "Aoyama Gosho",
		studio: "TMS Entertainment",
		views: 58_000_000,
		featured: true,
		next_air: Some("Tập 974 phát sóng hàng tuần"),
		quality_tag: "FHD",
		seasons: &[("conan", "Phần 1: Thám Tử Nhí")],
		source_url: "https://komorei.example/anime/conan",
	},
	// ── Additional entries for search / filter / pagination tests ──
	Entry {
		key: "one_piece",
		title: "One Piece: Hải Tặc Đại Chiến",
		original_title: "One Piece",
		cover: "https://images.unsplash.com/photo-1601850494422-3cf14624b0b3?w=600",
		banner: "https://images.unsplash.com/photo-1601850494422-3cf14624b0b3?w=1200",
		description: "Monkey D. Luffy lên đường tìm kiếm kho báu huyền thoại One Piece và trở thành Vua Hải Tặc.",
		episode_count: 1120,
		current_episode: "Tập 1110/1120",
		rating: 4.9,
		rating_count: 35000,
		status: AnimeStatus::Ongoing,
		release_year: "1999",
		genres: &["Phiêu Lưu", "Shounen", "Hành Động"],
		author: "Eiichiro Oda",
		studio: "Toei Animation",
		views: 62_000_000,
		featured: false,
		next_air: Some("Tập mới nhất hàng tuần Chủ Nhật"),
		quality_tag: "FHD",
		seasons: &[("one_piece", "Toàn bộ")],
		source_url: "https://komorei.example/anime/one-piece",
	},
	Entry {
		key: "attack_on_titan_final",
		title: "Attack on Titan: Mùa Cuối",
		original_title: "Shingeki no Kyojin: The Final Season",
		cover: "https://images.unsplash.com/photo-1618336753974-aae8e04506aa?w=600",
		banner: "https://images.unsplash.com/photo-1618336753974-aae8e04506aa?w=1200",
		description: "Cuộc chiến cuối cùng giữa loài người và các Titan đi đến hồi kết khi Eren Yeager thực hiện Rumble.",
		episode_count: 16,
		current_episode: "Tập 16/16 End",
		rating: 4.95,
		rating_count: 22000,
		status: AnimeStatus::Completed,
		release_year: "2023",
		genres: &["Hành Động", "Siêu Nhiên", "Kinh Dị"],
		author: "Hajime Isayama",
		studio: "MAPPA",
		views: 8_500_000,
		featured: false,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[("attack_on_titan_final", "The Final Season")],
		source_url: "https://komorei.example/anime/aot-final",
	},
	Entry {
		key: "chainsaw_man",
		title: "Chainsaw Man: Ma Sư Cưa Máy",
		original_title: "Chainsaw Man",
		cover: "https://images.unsplash.com/photo-1613919113640-25732ec5e61f?w=600",
		banner: "https://images.unsplash.com/photo-1613919113640-25732ec5e61f?w=1200",
		description: "Denji, một chàng trai nghèo mang trong mình con quỷ cưa máy, gia nhập Tổ chức Sát Quỷ để trả nợ và tìm kiếm một cuộc sống bình thường.",
		episode_count: 12,
		current_episode: "Tập 12/12 End",
		rating: 4.89,
		rating_count: 9800,
		status: AnimeStatus::Completed,
		release_year: "2023",
		genres: &["Hành Động", "Kinh Dị"],
		author: "Tatsuki Fujimoto",
		studio: "MAPPA",
		views: 3_100_000,
		featured: false,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[("chainsaw_man", "Mùa 1")],
		source_url: "https://komorei.example/anime/chainsaw-man",
	},
	Entry {
		key: "spy_family_2",
		title: "Spy x Family: Gia Đình Hành Động",
		original_title: "Spy x Family Season 2",
		cover: "https://images.unsplash.com/photo-1580477667995-2b94f01c9516?w=600",
		banner: "https://images.unsplash.com/photo-1580477667995-2b94f01c9516?w=1200",
		description: "Điệp viên Twilight phải xây dựng một gia đình giả để thực hiện sứ mệnh hòa bình, nhưng mỗi người trong gia đình đều có bí mật riêng.",
		episode_count: 12,
		current_episode: "Tập 12/12 End",
		rating: 4.88,
		rating_count: 7200,
		status: AnimeStatus::Completed,
		release_year: "2023",
		genres: &["Hành Động", "Hài"],
		author: "Tatsuya Endo",
		studio: "WIT Studio",
		views: 2_800_000,
		featured: false,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[("spy_family", "Mùa 1"), ("spy_family_2", "Mùa 2")],
		source_url: "https://komorei.example/anime/spy-family",
	},
	Entry {
		key: "death_note",
		title: "Death Note: Ghi Chép Tử Thần",
		original_title: "Death Note",
		cover: "https://images.unsplash.com/photo-1609743522653-52354667d2b6?w=600",
		banner: "https://images.unsplash.com/photo-1609743522653-52354667d2b6?w=1200",
		description: "Luffy — không, Light Yagami tìm thấy cuốn sổ tử thần và quyết định trở thành vị thần của thế giới mới, đọ trí với thám tử thiên tài L.",
		episode_count: 37,
		current_episode: "Full 37/37",
		rating: 4.97,
		rating_count: 28000,
		status: AnimeStatus::Completed,
		release_year: "2006",
		genres: &["Trinh Thám", "Siêu Nhiên", "Kinh Dị"],
		author: "Tsugumi Ohba",
		studio: "Madhouse",
		views: 12_000_000,
		featured: false,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[("death_note", "Toàn bộ")],
		source_url: "https://komorei.example/anime/death-note",
	},
	Entry {
		key: "fullmetal_alchemist",
		title: "Fullmetal Alchemist: Brotherhood",
		original_title: "Hagane no Renkinjutsushi: FULLMETAL ALCHEMIST",
		cover: "https://images.unsplash.com/photo-1560972550-aba3456b5564?w=600",
		banner: "https://images.unsplash.com/photo-1560972550-aba3456b5564?w=1200",
		description: "Hai anh em Elric sử dụng giả kim thuật bất hợp pháp để hồi sinh mẹ, đánh đổi bằng cơ thể. Họ lên đường tìm Hòn Đá ServiceProvider để lấy lại thân xác.",
		episode_count: 64,
		current_episode: "Full 64/64",
		rating: 4.99,
		rating_count: 20000,
		status: AnimeStatus::Completed,
		release_year: "2009",
		genres: &["Phiêu Lưu", "Hành Động", "Siêu Nhiên"],
		author: "Hiromu Arakawa",
		studio: "Bones",
		views: 9_500_000,
		featured: false,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[("fullmetal_alchemist", "Toàn bộ")],
		source_url: "https://komorei.example/anime/fma-brotherhood",
	},
	Entry {
		key: "my_hero_academia_s7",
		title: "Học Viện Anh Hùng: Mùa Cuối",
		original_title: "Boku no Hero Academia 7th Season",
		cover: "https://images.unsplash.com/photo-1611250188496-e966043a0629?w=600",
		banner: "https://images.unsplash.com/photo-1611250188496-e966043a0629?w=1200",
		description: "Deku và các bạn cùng lớp đối mặt với thử thách cuối cùng khi Liên Minh Fiendish phát động cuộc chiến tổng lực tiêu diệt Plus Ultra.",
		episode_count: 21,
		current_episode: "Tập 21/21 End",
		rating: 4.82,
		rating_count: 6500,
		status: AnimeStatus::Completed,
		release_year: "2024",
		genres: &["Hành Động", "Shounen"],
		author: "Kohei Horikoshi",
		studio: "Bones",
		views: 2_100_000,
		featured: false,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[
			("mha_1", "Mùa 1"),
			("mha_s7", "Mùa 7: Final"),
		],
		source_url: "https://komorei.example/anime/mha-s7",
	},
	Entry {
		key: "dress_up_darling",
		title: "100 Đồ Gái Cosplay",
		original_title: "Sono Bisque Doll wa Koi wo Suru",
		cover: "https://images.unsplash.com/photo-1578632292335-df3abbb0d586?w=600",
		banner: "https://images.unsplash.com/photo-1578632292335-df3abbb0d586?w=1200",
		description: "Gojo Wakana, một học sinh đam mê làm búp bê, tình cờ gặp Marin Kitagawa — cô gái xinh đẹp yêu cosplay. Họ bắt đầu hợp tác tạo trang phục cosplay đầu tiên.",
		episode_count: 12,
		current_episode: "Full 12/12",
		rating: 4.86,
		rating_count: 5800,
		status: AnimeStatus::Completed,
		release_year: "2022",
		genres: &["Tình Cảm", "Hài"],
		author: "Fukuda Shinichi",
		studio: "CloverWorks",
		views: 1_600_000,
		featured: false,
		next_air: None,
		quality_tag: "FHD",
		seasons: &[("dress_up_darling", "Mùa 1")],
		source_url: "https://komorei.example/anime/dress-up-darling",
	},
];

// ── Helpers ─────────────────────────────────────────────────────────────────

fn entry_by_key(key: &str) -> Option<&'static Entry> {
	CATALOG.iter().find(|e| e.key == key)
}

fn build_anime(entry: &Entry) -> Anime {
	Anime {
		key: String::from(entry.key),
		source_id: String::from(SOURCE_ID),
		title: String::from(entry.title),
		original_title: String::from(entry.original_title),
		cover: String::from(entry.cover),
		banner: Some(String::from(entry.banner)),
		description: Some(String::from(entry.description)),
		episode_count: entry.episode_count,
		current_episode: Some(String::from(entry.current_episode)),
		rating: Some(entry.rating),
		rating_count: Some(entry.rating_count),
		status: entry.status.clone(),
		release_year: Some(CategoryLink {
			name: String::from(entry.release_year),
			filters: Vec::new(),
		}),
		genres: entry
			.genres
			.iter()
			.map(|g| CategoryLink {
				name: String::from(*g),
				filters: Vec::new(),
			})
			.collect(),
		authors: vec![CategoryLink {
			name: String::from(entry.author),
			filters: Vec::new(),
		}],
		studio: Some(CategoryLink {
			name: String::from(entry.studio),
			filters: Vec::new(),
		}),
		season_of: None,
		countries: Vec::new(),
		is_featured: entry.featured,
		views: entry.views,
		next_episode_air_info: entry.next_air.map(String::from),
		quality_tag: Some(String::from(entry.quality_tag)),
		seasons: entry
			.seasons
			.iter()
			.map(|(id, title)| AnimeSeason::new(String::from(*id), String::from(*title)))
			.collect(),
		episodes: None,
		url: Some(String::from(entry.source_url)),
	}
}

fn build_lite(entry: &Entry) -> Anime {
	Anime {
		key: String::from(entry.key),
		source_id: String::from(SOURCE_ID),
		title: String::from(entry.title),
		original_title: String::from(entry.original_title),
		cover: String::from(entry.cover),
		episode_count: entry.episode_count,
		current_episode: Some(String::from(entry.current_episode)),
		rating: Some(entry.rating),
		rating_count: Some(entry.rating_count),
		views: entry.views,
		release_year: Some(CategoryLink {
			name: String::from(entry.release_year),
			filters: Vec::new(),
		}),
		status: entry.status.clone(),
		genres: entry
			.genres
			.iter()
			.map(|g| CategoryLink {
				name: String::from(*g),
				filters: Vec::new(),
			})
			.collect(),
		is_featured: entry.featured,
		..Default::default()
	}
}

fn generate_episodes(entry: &Entry, season_key: &str) -> Vec<Episode> {
	let count = core::cmp::min(entry.episode_count, 64); // cap for mega-series (Conan/One Piece)
	let now = 1_700_000_000_i64; // fixed timestamp for test determinism
	(1..=count)
		.map(|i| {
			Episode {
				key: format!("{season_key}_ep_{i}"),
				episode_number: format!("{i}"),
				title: Some(format!("Tập {i} - {}", entry.title)),
				thumbnail: Some(String::from(entry.cover)),
				date_uploaded: Some(now - (count - i) as i64 * 86_400),
				duration_seconds: Some(1440),
				quality: Some(String::from(entry.quality_tag)),
				url: None,
				language: Some(String::from("vi")),
				locked: false,
			}
		})
		.collect()
}

fn matches_filter(anime: &Anime, filters: &[FilterValue]) -> bool {
	for f in filters {
		match f {
			FilterValue::MultiSelect { id, included, .. } if id == "genres" => {
				if !included.is_empty()
					&& !anime.genres.iter().any(|g| included.contains(&g.name))
				{
					return false;
				}
			}
			FilterValue::Select { id, value } if id == "status" => {
				let match_status = match value.as_str() {
					"Đang phát" => anime.status == AnimeStatus::Ongoing,
					"Hoàn thành" => anime.status == AnimeStatus::Completed,
					_ => true,
				};
				if !match_status {
					return false;
				}
			}
			FilterValue::Sort { id, index, ascending } if id == "sort" => {
				// sorting is handled separately; presence here is fine
				let _ = (index, ascending);
			}
			_ => {}
		}
	}
	true
}

fn sort_entries(entries: &mut Vec<Anime>, filters: &[FilterValue]) {
	let sort = filters.iter().find_map(|f| match f {
		FilterValue::Sort { index, ascending, .. } => Some((*index, *ascending)),
		_ => None,
	});
	if let Some((idx, asc)) = sort {
		entries.sort_by(|a, b| {
			let ord = match idx {
				0 => b.rating.unwrap_or(0.0).partial_cmp(&a.rating.unwrap_or(0.0)).unwrap_or(Ordering::Equal),
				1 => b.views.cmp(&a.views),
				_ => a.title.cmp(&b.title),
			};
			if asc { ord.reverse() } else { ord }
		});
	}
}

// ── Source ──────────────────────────────────────────────────────────────────

struct FakeViSource;

impl Source for FakeViSource {
	fn new() -> Self { Self }

	fn get_search_anime_list(
		&self,
		query: Option<String>,
		page: i32,
		filters: Vec<FilterValue>,
	) -> Result<AnimePageResult> {
		let mut results: Vec<Anime> = CATALOG
			.iter()
			.map(|e| build_lite(e))
			.filter(|a| {
				if let Some(ref q) = query {
					let q = q.to_lowercase();
					a.title.to_lowercase().contains(&q)
						|| a.original_title.to_lowercase().contains(&q)
				} else {
					true
				}
			})
			.filter(|a| matches_filter(a, &filters))
			.collect();

		sort_entries(&mut results, &filters);

		let total = results.len();
		let start = ((page - 1) * PAGE_SIZE) as usize;
		let end = core::cmp::min(start + PAGE_SIZE as usize, total);
		let page_entries = if start < total { results[start..end].to_vec() } else { Vec::new() };
		let has_next = end < total;

		Ok(AnimePageResult {
			entries: page_entries,
			has_next_page: has_next,
		})
	}

	fn get_anime_update(
		&self,
		mut anime: Anime,
		needs_details: bool,
		needs_chapters: bool,
	) -> Result<Anime> {
		if let Some(entry) = entry_by_key(&anime.key) {
			if needs_details {
				let full = build_anime(entry);
				anime.copy_from(full);
			}
			if needs_chapters {
				// episodes for the CURRENT season (same key)
				anime.episodes = Some(generate_episodes(entry, &anime.key));
			}
		}
		Ok(anime)
	}

	fn get_stream_list(&self, _anime: Anime, _episode: Episode) -> Result<Vec<StreamInfo>> {
		let mut servers = vec![
			StreamInfo {
				key: String::from("hls"),
				name: String::from("Chill HLS"),
				quality: String::from("1080p FHD"),
			},
			StreamInfo {
				key: String::from("mp4_720"),
				name: String::from("MP4 720p"),
				quality: String::from("720p"),
			},
			StreamInfo {
				key: String::from("mp4_fhd"),
				name: String::from("MP4 Full HD"),
				quality: String::from("1080p"),
			},
		];
		// The default read/write surface ("hàm đọc/ghi dữ liệu ngầm") is
		// exercised here: prefer_fhd promotes the FHD servers to the top.
		if defaults_get::<bool>("prefer_fhd").unwrap_or(false) {
			let fhd = servers.remove(2);
			let hls = servers.remove(0);
			servers.insert(0, fhd);
			servers.push(hls);
		}
		Ok(servers)
	}

	fn get_stream(&self, _anime: Anime, _episode: Episode, stream: StreamInfo) -> Result<StreamData> {
		match stream.key.as_str() {
			"mp4_720" => Ok(StreamData {
				url: String::from(SAMPLE_MP4_720),
				stream_type: StreamType::MP4,
				is_content: true,
				headers: {
					let mut h = komorei::HashMap::new();
					h.insert(String::from("User-Agent"), String::from("Komorei/1.0"));
					h
				},
				subtitles: Vec::new(),
				intro: Some(RangeLong { start_ms: 0, end_ms: 90_000 }),
				outro: None,
			}),
			"mp4_fhd" => Ok(StreamData {
				url: String::from(SAMPLE_MP4_FHD),
				stream_type: StreamType::MP4,
				is_content: true,
				headers: {
					let mut h = komorei::HashMap::new();
					h.insert(String::from("User-Agent"), String::from("Komorei/1.0"));
					h
				},
				subtitles: Vec::new(),
				intro: Some(RangeLong { start_ms: 0, end_ms: 90_000 }),
				outro: Some(RangeLong { start_ms: 480_000, end_ms: 540_000 }),
			}),
			_ => Ok(StreamData {
				url: String::from(SAMPLE_HLS),
				stream_type: StreamType::HLS,
				is_content: true,
				headers: {
					let mut h = komorei::HashMap::new();
					h.insert(String::from("User-Agent"), String::from("Komorei/1.0"));
					h
				},
				subtitles: vec![SubtitleInfo {
					url: String::from("https://komorei.example/subs/vi.vtt"),
					language: String::from("vi"),
					label: Some(String::from("Tiếng Việt")),
					headers: komorei::HashMap::new(),
				}],
				intro: Some(RangeLong { start_ms: 0, end_ms: 90_000 }),
				outro: Some(RangeLong { start_ms: 480_000, end_ms: 540_000 }),
			}),
		}
	}
}

// ── ListingProvider ─────────────────────────────────────────────────────────

impl ListingProvider for FakeViSource {
	fn get_anime_list(&self, listing: Listing, page: i32) -> Result<AnimePageResult> {
		let entries: Vec<Anime> = match listing.id.as_str() {
			"ongoing" => CATALOG.iter()
				.filter(|e| e.status == AnimeStatus::Ongoing)
				.map(|e| build_lite(e))
				.collect(),
			"completed" => CATALOG.iter()
				.filter(|e| e.status == AnimeStatus::Completed)
				.map(|e| build_lite(e))
				.collect(),
			"popular" => {
				let mut v: Vec<Anime> = CATALOG.iter().map(|e| build_lite(e)).collect();
				v.sort_by(|a, b| b.views.cmp(&a.views));
				v
			}
			_ => CATALOG.iter().map(|e| build_lite(e)).collect(), // "latest" = all
		};
		let total = entries.len();
		let start = ((page - 1) * PAGE_SIZE) as usize;
		let end = core::cmp::min(start + PAGE_SIZE as usize, total);
		let page_entries = if start < total { entries[start..end].to_vec() } else { Vec::new() };
		Ok(AnimePageResult {
			entries: page_entries,
			has_next_page: end < total,
		})
	}
}

// ── Home ────────────────────────────────────────────────────────────────────

impl Home for FakeViSource {
	fn get_home(&self) -> Result<HomeLayout> {
		let featured: Vec<Anime> = CATALOG.iter()
			.filter(|e| e.featured)
			.map(|e| build_anime(e))
			.collect();
		let popular: Vec<Anime> = {
			let mut v: Vec<Anime> = CATALOG.iter().map(|e| build_lite(e)).collect();
			v.sort_by(|a, b| b.views.cmp(&a.views));
			v.truncate(5);
			v
		};
		let latest_episodes: Vec<AnimeWithEpisode> = CATALOG.iter().take(10).map(|e| {
			AnimeWithEpisode {
				anime: build_lite(e),
				episode: Episode {
					key: format!("{}_latest", e.key),
					episode_number: String::from(e.current_episode.split('/').next().unwrap_or("1")),
					title: Some(String::from(e.title)),
					date_uploaded: Some(1_700_000_000_i64),
					..Default::default()
				},
			}
		}).collect();

		Ok(HomeLayout {
			components: vec![
				HomeComponent {
					title: Some(String::from("Nổi Bật")),
					subtitle: None,
					value: HomeComponentValue::BigScroller {
						entries: featured,
						auto_scroll_interval: Some(5.0),
					},
				},
				HomeComponent {
					title: Some(String::from("Mới Cập Nhật")),
					subtitle: None,
					value: HomeComponentValue::AnimeEpisodeList {
						page_size: None,
						entries: latest_episodes,
						listing: Some(Listing { id: String::from("latest"), name: String::from("Mới nhất"), kind: ListingKind::List }),
					},
				},
				HomeComponent {
					title: Some(String::from("Phổ Biến Nhất")),
					subtitle: None,
					value: HomeComponentValue::AnimeList {
						ranking: true,
						page_size: Some(5),
						entries: popular.into_iter().map(|a| Link { title: a.title.clone(), subtitle: a.current_episode.clone(), value: Some(LinkValue::Anime(a)), ..Default::default() }).collect(),
						listing: Some(Listing { id: String::from("popular"), name: String::from("Phổ biến"), kind: ListingKind::List }),
					},
				},
				HomeComponent {
					title: Some(String::from("Thể Loại")),
					subtitle: None,
					value: HomeComponentValue::Filters(vec![
						"Action".into(), "Adventure".into(), "Fantasy".into(),
						"Super Natural".into(), "Romance".into(), "Sci-Fi".into(),
					]),
				},
			],
		})
	}
}

// ── DynamicFilters ──────────────────────────────────────────────────────────

impl DynamicFilters for FakeViSource {
	fn get_dynamic_filters(&self) -> Result<Vec<Filter>> {
		Ok(vec![
			TextFilter {
				id: "search".into(),
				title: Some("Tìm kiếm".into()),
				placeholder: Some("Tên anime...".into()),
				..Default::default()
			}.into(),
			SortFilter {
				id: "sort".into(),
				title: Some("Sắp xếp".into()),
				can_ascend: true,
				options: vec!["Đánh giá".into(), "Phổ biến".into(), "A-Z".into()],
				..Default::default()
			}.into(),
			MultiSelectFilter {
				id: "genres".into(),
				title: Some("Thể loại".into()),
				is_genre: true,
				can_exclude: false,
				uses_tag_style: true,
				options: vec![
					"Hành Động".into(), "Phiêu Lưu".into(), "Giả Tưởng".into(),
					"Siêu Nhiên".into(), "Shounen".into(), "Tình Cảm".into(),
					"Sci-Fi".into(), "Trinh Thám".into(), "Chuyển Sinh".into(),
					"Hài".into(), "Kinh Dị".into(),
				],
				..Default::default()
			}.into(),
			SelectFilter {
				id: "status".into(),
				title: Some("Trạng thái".into()),
				options: vec!["Tất cả".into(), "Đang phát".into(), "Hoàn thành".into()],
				..Default::default()
			}.into(),
			RangeFilter {
				id: "year".into(),
				title: Some("Năm phát hành".into()),
				min: Some(1996.0),
				max: Some(2025.0),
				decimal: false,
				..Default::default()
			}.into(),
			Filter::note("Nguồn dữ liệu demo — Komorei Fake (VI)"),
		])
	}
}

// ── DynamicSettings ─────────────────────────────────────────────────────────

impl DynamicSettings for FakeViSource {
	fn get_dynamic_settings(&self) -> Result<Vec<Setting>> {
		Ok(vec![
			ToggleSetting {
				key: "prefer_fhd".into(),
				title: "Ưu tiên 1080p".into(),
				notification: Some("Đã thay đổi ưu tiên chất lượng".into()),
				refreshes: Some(vec!["settings".into()]),
				..Default::default()
			}.into(),
			ToggleSetting {
				key: "show_intro".into(),
				title: "Hiển thị nút bỏ intro".into(),
				..Default::default()
			}.into(),
		])
	}
}

// ── DynamicListings ─────────────────────────────────────────────────────────

impl DynamicListings for FakeViSource {
	fn get_dynamic_listings(&self) -> Result<Vec<Listing>> {
		Ok(vec![
			Listing { id: String::from("latest"), name: String::from("Mới nhất"), kind: ListingKind::List },
			Listing { id: String::from("popular"), name: String::from("Phổ biến"), kind: ListingKind::List },
			Listing { id: String::from("ongoing"), name: String::from("Đang phát"), kind: ListingKind::List },
			Listing { id: String::from("completed"), name: String::from("Hoàn thành"), kind: ListingKind::List },
		])
	}
}

// ── NotificationHandler ─────────────────────────────────────────────────────

impl NotificationHandler for FakeViSource {
	fn handle_notification(&self, key: String) {
		// defaults value changed — no-op in demo
		let _ = key;
	}
}

// ── DeepLinkHandler ─────────────────────────────────────────────────────────

impl DeepLinkHandler for FakeViSource {
	fn handle_deep_link(&self, url: String) -> Result<Option<DeepLinkResult>> {
		if let Some(idx) = url.find("/anime/") {
			let key = url[idx + "/anime/".len()..].trim_end_matches('/');
			if entry_by_key(key).is_some() {
				return Ok(Some(DeepLinkResult::Anime { key: String::from(key) }));
			}
		}
		if let Some(idx) = url.find("/watch/") {
			let rest = &url[idx + "/watch/".len()..].trim_end_matches('/');
			let parts: Vec<&str> = rest.splitn(2, '/').collect();
			if parts.len() == 2 {
				let anime_key = parts[0];
				let ep_key = parts[1];
				if entry_by_key(anime_key).is_some() {
					return Ok(Some(DeepLinkResult::Episode {
						anime_key: String::from(anime_key),
						key: String::from(ep_key),
					}));
				}
			}
		}
		if let Some(idx) = url.find("/list/") {
			let id = url[idx + "/list/".len()..].trim_end_matches('/');
			return Ok(Some(DeepLinkResult::Listing(Listing {
				id: String::from(id),
				name: String::from(id),
				kind: ListingKind::List,
			})));
		}
		Ok(None)
	}
}

// ── MigrationHandler ────────────────────────────────────────────────────────

impl MigrationHandler for FakeViSource {
	fn handle_anime_migration(&self, key: String) -> Result<String> { Ok(key) }
	fn handle_episode_migration(&self, _anime_key: String, episode_key: String) -> Result<String> { Ok(episode_key) }
}

// ── Segment interceptors ────────────────────────────────────────────────────

impl SegmentUrlInterceptor for FakeViSource {
	fn intercept_segment_url(&self, _stream_data: Option<&StreamData>, url: String) -> String { url }
}

impl SegmentDataInterceptor for FakeViSource {
	fn intercept_segment_data(
		&self,
		_stream_data: Option<&StreamData>,
		_url: String,
		data: &[u8],
	) -> Vec<u8> {
		data.to_vec()
	}
}

// ── register ────────────────────────────────────────────────────────────────

register_source!(
	FakeViSource,
	ListingProvider,
	Home,
	DynamicFilters,
	DynamicSettings,
	DynamicListings,
	NotificationHandler,
	DeepLinkHandler,
	MigrationHandler,
	SegmentUrlInterceptor,
	SegmentDataInterceptor
);
