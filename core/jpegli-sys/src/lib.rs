//! Raw bindings to the photon jpegli shim (see `shim/photon_jpegli.h`).
//! Use the safe wrapper in `photon-core` instead of calling these directly.

use std::os::raw::{c_char, c_float, c_int};

#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct photon_jpegli_options {
    pub distance: c_float,
    pub chroma_420: c_int,
    pub progressive_level: c_int,
}

extern "C" {
    pub fn photon_jpegli_encode_rgb(
        rgb: *const u8,
        width: u32,
        height: u32,
        options: *const photon_jpegli_options,
        out: *mut *mut u8,
        out_len: *mut usize,
        err: *mut c_char,
        err_len: usize,
    ) -> c_int;

    pub fn photon_jpegli_free(buf: *mut u8);
}
