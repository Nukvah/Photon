// Minimal C API over jpegli for photon-core. Keeps libjpeg's setjmp/longjmp
// error handling on the C++ side so no unwinding ever crosses into Rust.
#ifndef PHOTON_JPEGLI_H_
#define PHOTON_JPEGLI_H_

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
  float distance;        // Butteraugli distance: 1.0 ~ visually lossless, higher = smaller.
  int chroma_420;        // 1 = 4:2:0 chroma subsampling, 0 = 4:4:4.
  int progressive_level; // 0 = baseline sequential (most compatible), 1-2 = progressive.
} photon_jpegli_options;

// Encodes tightly packed 8-bit sRGB pixels (width * height * 3 bytes).
// On success returns 0 and sets *out / *out_len (free with photon_jpegli_free).
// On failure returns non-zero and writes a message into err (if err_len > 0).
int photon_jpegli_encode_rgb(const uint8_t* rgb, uint32_t width, uint32_t height,
                             const photon_jpegli_options* options,
                             uint8_t** out, size_t* out_len,
                             char* err, size_t err_len);

void photon_jpegli_free(uint8_t* buf);

#ifdef __cplusplus
}
#endif

#endif  // PHOTON_JPEGLI_H_
