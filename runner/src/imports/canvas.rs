//! `canvas` module: bitmap canvas API. Not implemented in milestone 1 — every
//! entry is registered (so any `.krx` instantiates) but returns an error code
//! (`-1` = InvalidContext) so sources fall back.

#![allow(clippy::too_many_arguments)]

use wasmi::{Caller, Linker};

use crate::state::RunnerData;
use crate::imports::stub_code;

pub fn register(linker: &mut Linker<RunnerData>) {
	linker.func_wrap("canvas", "new_context", new_context).unwrap();
	linker.func_wrap("canvas", "set_transform", set_transform).unwrap();
	linker.func_wrap("canvas", "copy_image", copy_image).unwrap();
	linker.func_wrap("canvas", "draw_image", draw_image).unwrap();
	linker.func_wrap("canvas", "fill", fill).unwrap();
	linker.func_wrap("canvas", "stroke", stroke).unwrap();
	linker.func_wrap("canvas", "draw_text", draw_text).unwrap();
	linker.func_wrap("canvas", "get_image", get_image).unwrap();
	linker.func_wrap("canvas", "new_font", new_font).unwrap();
	linker.func_wrap("canvas", "system_font", system_font).unwrap();
	linker.func_wrap("canvas", "load_font", load_font).unwrap();
	linker.func_wrap("canvas", "new_image", new_image).unwrap();
	linker.func_wrap("canvas", "get_image_data", get_image_data).unwrap();
	linker.func_wrap("canvas", "get_image_width", get_image_width).unwrap();
	linker.func_wrap("canvas", "get_image_height", get_image_height).unwrap();
}

fn new_context(_caller: Caller<'_, RunnerData>, _width: f32, _height: f32) -> i32 {
	stub_code()
}

fn set_transform(
	_caller: Caller<'_, RunnerData>,
	_context: i32,
	_translate_x: f32,
	_translate_y: f32,
	_scale_x: f32,
	_scale_y: f32,
	_rotate_angle: f32,
) -> i32 {
	stub_code()
}

fn copy_image(
	_caller: Caller<'_, RunnerData>,
	_context: i32,
	_image: i32,
	_src_x: f32,
	_src_y: f32,
	_src_width: f32,
	_src_height: f32,
	_dst_x: f32,
	_dst_y: f32,
	_dst_width: f32,
	_dst_height: f32,
) -> i32 {
	stub_code()
}

fn draw_image(
	_caller: Caller<'_, RunnerData>,
	_context: i32,
	_image: i32,
	_dst_x: f32,
	_dst_y: f32,
	_dst_width: f32,
	_dst_height: f32,
) -> i32 {
	stub_code()
}

fn fill(
	_caller: Caller<'_, RunnerData>,
	_context: i32,
	_path: i32,
	_r: f32,
	_g: f32,
	_b: f32,
	_a: f32,
) -> i32 {
	stub_code()
}

fn stroke(_caller: Caller<'_, RunnerData>, _context: i32, _path: i32, _style: i32) -> i32 {
	stub_code()
}

fn draw_text(
	_caller: Caller<'_, RunnerData>,
	_context: i32,
	_text_ptr: u32,
	_text_len: u32,
	_size: f32,
	_x: f32,
	_y: f32,
	_font: i32,
	_r: f32,
	_g: f32,
	_b: f32,
	_a: f32,
) -> i32 {
	stub_code()
}

fn get_image(_caller: Caller<'_, RunnerData>, _context: i32) -> i32 {
	stub_code()
}

fn new_font(_caller: Caller<'_, RunnerData>, _name_ptr: u32, _name_len: u32) -> i32 {
	stub_code()
}

fn system_font(_caller: Caller<'_, RunnerData>, _weight: i32) -> i32 {
	stub_code()
}

fn load_font(_caller: Caller<'_, RunnerData>, _url_ptr: u32, _url_len: u32) -> i32 {
	stub_code()
}

fn new_image(_caller: Caller<'_, RunnerData>, _data_ptr: u32, _data_len: u32) -> i32 {
	stub_code()
}

fn get_image_data(_caller: Caller<'_, RunnerData>, _image_rid: i32) -> i32 {
	stub_code()
}

fn get_image_width(_caller: Caller<'_, RunnerData>, _image_rid: i32) -> f32 {
	0.0
}

fn get_image_height(_caller: Caller<'_, RunnerData>, _image_rid: i32) -> f32 {
	0.0
}