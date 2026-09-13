//! The wasm engine: instantiates the `.krx` payload with wasmi, calls exports
//! and decodes the result buffers (the ABI layout owned by `crates/lib`:
//! success `[len i32][cap i32][postcard...]`, Message error
//! `[len = -1][cap][len][utf8 msg]`, other errors = raw negative code).

use std::sync::Arc;

use serde::de::DeserializeOwned;
use wasmi::{Engine, Instance, Linker, Memory, Module, Store, TypedFunc};

use crate::error::RunnerError;
use crate::host::KomoreiHost;
use crate::imports;
use crate::state::RunnerData;

pub struct EngineState {
	pub(crate) store: Store<RunnerData>,
	pub(crate) instance: Instance,
}

// wasmi's `Store` is arena-based and not Send/Sync by default; every access is
// serialized through the `KomoreiRunner` mutex on a single thread, so this is
// sound.
unsafe impl Send for EngineState {}

impl EngineState {
	pub fn load(wasm: &[u8], host: Arc<dyn KomoreiHost>) -> Result<Self, RunnerError> {
		let engine = Engine::default();
		let module = Module::new(&engine, wasm)?;
		let data = RunnerData {
			store: crate::state::GlobalStore::new(),
			host,
		};
		let mut store = Store::new(&engine, data);
		let mut linker = Linker::new(&engine);
		imports::register(&mut linker);
		let instance = linker.instantiate_and_start(&mut store, &module)?;
		// resolve the memory export (it can grow later; we always fetch the
		// current memory from the instance, never cache the handle)
		instance
			.get_export(&store, "memory")
			.and_then(|e| e.into_memory())
			.ok_or_else(|| RunnerError::ExportMissing { name: "memory".into() })?;
		Ok(Self { store, instance })
	}

	// -- export helpers ------------------------------------------------------

	pub(crate) fn get_typed<Params, Results>(
		&self,
		name: &str,
	) -> Result<TypedFunc<Params, Results>, RunnerError>
	where
		Params: wasmi::WasmParams,
		Results: wasmi::WasmResults,
	{
		let func = self
			.instance
			.get_func(&self.store, name)
			.ok_or_else(|| RunnerError::ExportMissing { name: name.into() })?;
		func.typed(&self.store).map_err(RunnerError::from)
	}

	pub(crate) fn call_start(&mut self) -> Result<(), RunnerError> {
		let f = self.get_typed::<(), ()>("start")?;
		f.call(&mut self.store, ()).map_err(RunnerError::wasm)
	}

	pub(crate) fn call0(&mut self, name: &str) -> Result<i32, RunnerError> {
		let f = self.get_typed::<(), (i32,)>(name)?;
		let (result,) = f.call(&mut self.store, ()).map_err(RunnerError::wasm)?;
		Ok(result)
	}

	pub(crate) fn call1(&mut self, name: &str, a0: i32) -> Result<i32, RunnerError> {
		let f = self.get_typed::<(i32,), (i32,)>(name)?;
		let (result,) = f.call(&mut self.store, (a0,)).map_err(RunnerError::wasm)?;
		Ok(result)
	}

	pub(crate) fn call2(&mut self, name: &str, a0: i32, a1: i32) -> Result<i32, RunnerError> {
		let f = self.get_typed::<(i32, i32), (i32,)>(name)?;
		let (result,) = f.call(&mut self.store, (a0, a1)).map_err(RunnerError::wasm)?;
		Ok(result)
	}

	pub(crate) fn call3(&mut self, name: &str, a0: i32, a1: i32, a2: i32) -> Result<i32, RunnerError> {
		let f = self.get_typed::<(i32, i32, i32), (i32,)>(name)?;
		let (result,) = f.call(&mut self.store, (a0, a1, a2)).map_err(RunnerError::wasm)?;
		Ok(result)
	}

	// -- argument descriptors ------------------------------------------------

	/// Stores a postcard-encoded argument descriptor (for `std::read::<T>`).
	pub(crate) fn encode<T: serde::Serialize>(&mut self, value: &T) -> Result<i32, RunnerError> {
		let rid = self.store.data_mut().store.store_encoded(value)?;
		Ok(rid)
	}

	/// Stores raw UTF-8 bytes (for `std::read_string`, e.g. the search query).
	pub(crate) fn encode_raw_string(&mut self, value: &str) -> i32 {
		self.store
			.data_mut()
			.store
			.store_raw(value.as_bytes().to_vec())
	}

	// -- result decoding -----------------------------------------------------

	fn memory(&self) -> Result<Memory, RunnerError> {
		self.instance
			.get_export(&self.store, "memory")
			.and_then(|e| e.into_memory())
			.ok_or_else(|| RunnerError::ExportMissing { name: "memory".into() })
	}

	fn read_mem(&self, ptr: usize, len: usize) -> Result<Vec<u8>, RunnerError> {
		let memory = self.memory()?;
		let mut buf = vec![0u8; len];
		memory.read(&self.store, ptr, &mut buf).map_err(RunnerError::wasm)?;
		Ok(buf)
	}

	fn free(&mut self, ptr: i32) {
		if let Ok(f) = self.get_typed::<(i32,), ()>("free_result") {
			let _ = f.call(&mut self.store, (ptr,));
		}
	}

	/// Reads the *raw* results payload (the postcard bytes after the 8-byte
	/// `[len][cap]` header), freeing the wasm buffer on the way out.
	pub(crate) fn decode_raw(&mut self, ptr: i32) -> Result<Vec<u8>, RunnerError> {
		if ptr < 0 {
			return Err(RunnerError::Source { code: ptr, message: String::new() });
		}
		let len_bytes = self.read_mem(ptr as usize, 4)?;
		let len = i32::from_le_bytes(len_bytes.try_into().unwrap());
		if len < 0 {
			// Message error: [len=-1][cap][msg_len][utf8 msg]
			let msg_len_bytes = self.read_mem((ptr as usize) + 8, 4);
			let msg_len = match msg_len_bytes {
				Ok(bytes) => i32::from_le_bytes(bytes.try_into().unwrap()) as usize,
				Err(_) => 0,
			};
			let msg = if msg_len > 0 {
				self.read_mem((ptr as usize) + 12, msg_len).unwrap_or_default()
			} else {
				Vec::new()
			};
			self.free(ptr);
			return Err(RunnerError::Source {
				code: -1,
				message: String::from_utf8_lossy(&msg).into_owned(),
			});
		}
		if len < 8 {
			// a success buffer is at least the 8-byte header
			self.free(ptr);
			return Err(RunnerError::InvalidResult);
		}
		let payload_len = len as usize - 8;
		let payload = self.read_mem((ptr as usize) + 8, payload_len)?;
		self.free(ptr);
		Ok(payload)
	}

	pub(crate) fn decode<T: DeserializeOwned>(&mut self, ptr: i32) -> Result<T, RunnerError> {
		let payload = self.decode_raw(ptr)?;
		postcard::from_bytes(&payload).map_err(RunnerError::from)
	}
}