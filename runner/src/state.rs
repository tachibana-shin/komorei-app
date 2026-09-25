//! Runner-side state: the rid store and the wasmi store user data.

use std::collections::HashMap;
use std::sync::Arc;

use crate::host::KomoreiHost;

pub type Rid = i32;

/// Items referenced by wasm descriptors (rids).
pub enum StoreItem {
	/// An in-flight net request (holds the response once `send` succeeded).
	Request(crate::imports::net::RequestState),
	/// An opaque HTML DOM handle owned by the Kotlin host (Jsoup).
	Html(i64),
	/// An opaque JS context handle owned by the Kotlin host (WebView).
	JsContext(i64),
	/// An opaque JS value handle owned by the Kotlin host.
	JsValue(i64),
	/// An opaque JS webview handle owned by the Kotlin host.
	JsWebView(i64),
	/// Raw bytes read back by the wasm via `std::read_buffer`.
	/// Strings are stored as raw UTF-8; typed values as postcard bytes.
	Encoded(Vec<u8>),
}

/// The rid store, mirroring the aidoku `GlobalStore` semantics:
/// rids start at 1 and only ever grow; the wasm frees items by calling
/// `std::destroy(rid)`, which also forwards HTML handles to
/// `host.html_destroy(handle)`.
pub struct GlobalStore {
	pointer: Rid,
	storage: HashMap<Rid, StoreItem>,
}

impl GlobalStore {
	pub fn new() -> Self {
		Self {
			pointer: 1,
			storage: HashMap::new(),
		}
	}

	pub fn store(&mut self, item: StoreItem) -> Rid {
		let rid = self.pointer;
		self.storage.insert(rid, item);
		self.pointer += 1;
		rid
	}

	pub fn store_encoded<T: serde::Serialize>(
		&mut self,
		value: &T,
	) -> Result<Rid, postcard::Error> {
		let encoded = postcard::to_allocvec(value)?;
		Ok(self.store(StoreItem::Encoded(encoded)))
	}

	pub fn store_raw(&mut self, bytes: Vec<u8>) -> Rid {
		self.store(StoreItem::Encoded(bytes))
	}

	pub fn get(&self, rid: Rid) -> Option<&StoreItem> {
		self.storage.get(&rid)
	}

	pub fn get_mut(&mut self, rid: Rid) -> Option<&mut StoreItem> {
		self.storage.get_mut(&rid)
	}

	/// Raw bytes of an `Encoded` item (used by `buffer_len` / `read_buffer`).
	pub fn item_bytes(&self, rid: Rid) -> Option<&[u8]> {
		match self.storage.get(&rid) {
			Some(StoreItem::Encoded(d)) => Some(d),
			_ => None,
		}
	}

	pub fn remove(&mut self, rid: Rid) {
		self.storage.remove(&rid);
	}
}

/// User data stored inside the wasmi `Store`.
pub struct RunnerData {
	pub store: GlobalStore,
	/// The Kotlin host that implements all real IO.
	pub host: Arc<dyn KomoreiHost>,
}
