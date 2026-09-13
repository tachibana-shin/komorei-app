//! `env` module: logs, sleep, partial results.

use wasmi::{Caller, Linker};

use crate::{abi, state::RunnerData};

pub fn register(linker: &mut Linker<RunnerData>) {
	linker.func_wrap("env", "abort", abort).unwrap();
	linker.func_wrap("env", "print", print).unwrap();
	linker.func_wrap("env", "sleep", sleep).unwrap();
	linker.func_wrap("env", "send_partial_result", send_partial_result).unwrap();
}

/// Called when the source panics / aborts.
fn abort(caller: Caller<'_, RunnerData>) {
	caller.data().host.log_abort();
}

/// `println!` from the source.
fn print(caller: Caller<'_, RunnerData>, ptr: u32, len: u32) {
	let message = abi::read_string(&caller, ptr, len).unwrap_or_default();
	caller.data().host.log_print(message);
}

fn sleep(_caller: Caller<'_, RunnerData>, seconds: i32) {
	std::thread::sleep(std::time::Duration::from_secs(seconds.max(0) as u64));
}

/// Partial results (home layout / anime chunks while `get_home` streams) are
/// not forwarded in milestone 1.
fn send_partial_result(_caller: Caller<'_, RunnerData>, _ptr: i32) {
	// the wasm side frees the encoded value itself
	// (crate::lib::imports::std::send_partial_result calls free_result).
}