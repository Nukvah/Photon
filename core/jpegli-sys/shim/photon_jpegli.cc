#include "photon_jpegli.h"

#include <setjmp.h>
#include <stdlib.h>
#include <string.h>

#include "lib/jpegli/encode.h"

namespace {

struct ErrorManager {
  jpeg_error_mgr pub;
  jmp_buf jump;
  char message[JMSG_LENGTH_MAX];
  // Output buffer lives here (not in a plain local) so it is still valid after longjmp.
  unsigned char* dest;
  unsigned long dest_size;
};

void OnError(j_common_ptr cinfo) {
  ErrorManager* mgr = reinterpret_cast<ErrorManager*>(cinfo->err);
  (*cinfo->err->format_message)(cinfo, mgr->message);
  longjmp(mgr->jump, 1);
}

void OnMessage(j_common_ptr, int) {}  // Silence warnings and trace output.

void CopyError(const char* msg, char* err, size_t err_len) {
  if (err == nullptr || err_len == 0) return;
  strncpy(err, msg, err_len - 1);
  err[err_len - 1] = '\0';
}

}  // namespace

extern "C" int photon_jpegli_encode_rgb(const uint8_t* rgb, uint32_t width, uint32_t height,
                                        const photon_jpegli_options* options,
                                        uint8_t** out, size_t* out_len,
                                        char* err, size_t err_len) {
  if (rgb == nullptr || options == nullptr || out == nullptr || out_len == nullptr ||
      width == 0 || height == 0) {
    CopyError("invalid arguments", err, err_len);
    return 1;
  }
  *out = nullptr;
  *out_len = 0;

  jpeg_compress_struct cinfo;
  ErrorManager errmgr;
  memset(&cinfo, 0, sizeof(cinfo));
  memset(&errmgr, 0, sizeof(errmgr));
  cinfo.err = jpegli_std_error(&errmgr.pub);
  errmgr.pub.error_exit = OnError;
  errmgr.pub.emit_message = OnMessage;

  if (setjmp(errmgr.jump)) {
    jpegli_destroy_compress(&cinfo);
    free(errmgr.dest);
    CopyError(errmgr.message, err, err_len);
    return 2;
  }

  jpegli_create_compress(&cinfo);
  jpegli_mem_dest(&cinfo, &errmgr.dest, &errmgr.dest_size);

  cinfo.image_width = width;
  cinfo.image_height = height;
  cinfo.input_components = 3;
  cinfo.in_color_space = JCS_RGB;
  jpegli_set_defaults(&cinfo);
  // force_baseline = TRUE keeps quantization tables 8-bit, which every decoder supports.
  jpegli_set_distance(&cinfo, options->distance, TRUE);
  jpegli_set_progressive_level(&cinfo, options->progressive_level);
  cinfo.optimize_coding = TRUE;
  if (options->chroma_420) {
    cinfo.comp_info[0].h_samp_factor = 2;
    cinfo.comp_info[0].v_samp_factor = 2;
  } else {
    cinfo.comp_info[0].h_samp_factor = 1;
    cinfo.comp_info[0].v_samp_factor = 1;
  }
  for (int c = 1; c < 3; ++c) {
    cinfo.comp_info[c].h_samp_factor = 1;
    cinfo.comp_info[c].v_samp_factor = 1;
  }

  jpegli_start_compress(&cinfo, TRUE);
  const size_t stride = static_cast<size_t>(width) * 3;
  while (cinfo.next_scanline < cinfo.image_height) {
    JSAMPROW row = const_cast<JSAMPROW>(rgb + cinfo.next_scanline * stride);
    jpegli_write_scanlines(&cinfo, &row, 1);
  }
  jpegli_finish_compress(&cinfo);
  jpegli_destroy_compress(&cinfo);

  *out = errmgr.dest;
  *out_len = errmgr.dest_size;
  return 0;
}

extern "C" void photon_jpegli_free(uint8_t* buf) { free(buf); }
