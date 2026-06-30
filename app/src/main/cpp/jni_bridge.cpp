#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <atomic>
#include <new>

#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <libretro.h>

#define LOG_TAG "JustGBA-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* ------------------------------------------------------------------ */
/*  Frontend callback state                                           */
/* ------------------------------------------------------------------ */

static void log_callback(enum retro_log_level level, const char* fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int prio = (level == RETRO_LOG_ERROR) ? ANDROID_LOG_ERROR
             : (level == RETRO_LOG_WARN)  ? ANDROID_LOG_WARN
             : ANDROID_LOG_INFO;
    __android_log_vprint(prio, "gpSP", fmt, ap);
    va_end(ap);
}

static retro_log_printf_t       log_cb              = nullptr;
static retro_video_refresh_t    video_cb            = nullptr;
static retro_audio_sample_batch_t audio_batch_cb    = nullptr;
static retro_input_poll_t       input_poll_cb       = nullptr;
static retro_input_state_t      input_state_cb      = nullptr;
static retro_environment_t      environ_cb          = nullptr;

/* ------------------------------------------------------------------ */
/*  Video buffer                                                      */
/* ------------------------------------------------------------------ */

static constexpr int SCREEN_W = 240;
static constexpr int SCREEN_H = 160;
static constexpr int SCREEN_PITCH = 240;

static ANativeWindow* native_window = nullptr;
static int       video_width    = 0;
static int       video_height   = 0;
static size_t    video_pitch    = 0;
static std::atomic<bool> frame_ready { false };
static std::atomic<bool> skip_render { false };

/* ------------------------------------------------------------------ */
/*  Audio ring buffer                                                 */
/* ------------------------------------------------------------------ */

static constexpr int AUDIO_RING_CAPACITY = 48000 * 2;
static int16_t  audio_ring[AUDIO_RING_CAPACITY];
static std::atomic<int> audio_wpos { 0 };
static std::atomic<int> audio_rpos { 0 };
static std::atomic<bool> audio_overrun { false };

/* ------------------------------------------------------------------ */
/*  Forward declarations                                               */
/* ------------------------------------------------------------------ */

extern "C" {
void retro_init(void);
void retro_deinit(void);
bool retro_load_game(const struct retro_game_info* info);
void retro_unload_game(void);
void retro_run(void);
void retro_reset(void);
void retro_set_environment(retro_environment_t cb);
void retro_set_video_refresh(retro_video_refresh_t cb);
void retro_set_audio_sample_batch(retro_audio_sample_batch_t cb);
void retro_set_input_poll(retro_input_poll_t cb);
unsigned retro_api_version(void);
void* retro_get_memory_data(unsigned id);
size_t retro_get_memory_size(unsigned id);
size_t retro_serialize_size(void);
bool retro_serialize(void* data, size_t size);
bool retro_unserialize(const void* data, size_t size);
}

/* ------------------------------------------------------------------ */
/*  Input state (set from Kotlin)                                     */
/* ------------------------------------------------------------------ */

static std::atomic<uint32_t> joypad_bits { 0 };
static std::atomic<float> ff_speed_multiplier { 0.0f };
static std::atomic<bool> mute_ff_audio { true };

/* ------------------------------------------------------------------ */
/*  System directory path (set from Kotlin)                            */
/* ------------------------------------------------------------------ */

static char system_dir[512] = { 0 };
static char save_dir[512]   = { 0 };

/* ------------------------------------------------------------------ */
/*  Libretro environment callback                                     */
/* ------------------------------------------------------------------ */

static bool environ_cb_func(unsigned cmd, void* data) {
    switch (cmd) {
    case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
        auto* cb = static_cast<retro_log_callback*>(data);
        cb->log = log_callback;
        return true;
    }
    case RETRO_ENVIRONMENT_GET_PERF_INTERFACE: {
        auto* cb = static_cast<retro_perf_callback*>(data);
        cb->get_time_usec      = []() -> retro_time_t { return 0; };
        cb->get_cpu_features   = []() -> uint64_t { return 0; };
        cb->get_perf_counter   = []() -> retro_perf_tick_t { return 0; };
        cb->perf_register      = [](retro_perf_counter*) {};
        cb->perf_start         = [](retro_perf_counter*) {};
        cb->perf_stop          = [](retro_perf_counter*) {};
        cb->perf_log           = []() {};
        return true;
    }
    case RETRO_ENVIRONMENT_GET_VFS_INTERFACE: {
        return false;
    }
    case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
        auto fmt = *static_cast<const retro_pixel_format*>(data);
        return (fmt == RETRO_PIXEL_FORMAT_RGB565);
    }
    case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
        if (system_dir[0]) {
            *static_cast<const char**>(data) = system_dir;
            return true;
        }
        return false;
    }
    case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
        if (save_dir[0]) {
            *static_cast<const char**>(data) = save_dir;
            return true;
        }
        return false;
    }
    case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS: {
        return true;
    }
    case RETRO_ENVIRONMENT_SET_FASTFORWARDING_OVERRIDE: {
        return true;
    }
    case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS: {
        return true;
    }
    case RETRO_ENVIRONMENT_SET_MEMORY_MAPS: {
        return true;
    }
    case RETRO_ENVIRONMENT_GET_VARIABLE: {
        auto* var = static_cast<retro_variable*>(data);
        if (var && var->key && strcmp(var->key, "gpsp_drc") == 0) {
            var->value = "enabled";
            return true;
        }
        return false;
    }
    case RETRO_ENVIRONMENT_SET_VARIABLES: {
        return true;
    }
    case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
        return false;
    }
    case RETRO_ENVIRONMENT_GET_CAN_DUPE: {
        auto* val = static_cast<bool*>(data);
        *val = true;
        return true;
    }
    case RETRO_ENVIRONMENT_SET_ROTATION: {
        return true;
    }
    case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME: {
        return false;
    }
    default:
        return false;
    }
}

/* ------------------------------------------------------------------ */
/*  Video callback                                                     */
/* ------------------------------------------------------------------ */

static void video_refresh_cb(const void* data, unsigned width,
                             unsigned height, size_t pitch) {
    if (skip_render.load(std::memory_order_relaxed) || !data || !native_window) return;

    const int SCALE = 4;
    unsigned out_width = width * SCALE;
    unsigned out_height = height * SCALE;

    ANativeWindow_setBuffersGeometry(native_window, out_width, out_height, WINDOW_FORMAT_RGB_565);

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(native_window, &buffer, nullptr) == 0) {
        const uint8_t* src = static_cast<const uint8_t*>(data);
        uint8_t* dst = static_cast<uint8_t*>(buffer.bits);

        size_t dst_stride_bytes = buffer.stride * 2;

        for (unsigned y = 0; y < height; y++) {
            const uint16_t* src_row = reinterpret_cast<const uint16_t*>(src + (y * pitch));

            uint16_t* dst_row = reinterpret_cast<uint16_t*>(dst + (y * SCALE) * dst_stride_bytes);
            for (unsigned x = 0; x < width; x++) {
                uint16_t pixel = src_row[x];
                for (int s = 0; s < SCALE; s++) {
                    dst_row[x * SCALE + s] = pixel;
                }
            }

            for (int s = 1; s < SCALE; s++) {
                uint8_t* next_dst_row = dst + (y * SCALE + s) * dst_stride_bytes;
                memcpy(next_dst_row, dst_row, out_width * 2);
            }
        }

        ANativeWindow_unlockAndPost(native_window);
    }
}

/* ------------------------------------------------------------------ */
/*  Audio callback                                                     */
/* ------------------------------------------------------------------ */

static size_t audio_batch_fn(const int16_t* data, size_t frames) {
    int samples = static_cast<int>(frames) * 2;
    int wp = audio_wpos.load(std::memory_order_relaxed);
    int rp = audio_rpos.load(std::memory_order_acquire);

    int filled = (wp >= rp) ? (wp - rp) : (AUDIO_RING_CAPACITY - rp + wp);
    int free_space = AUDIO_RING_CAPACITY - filled - 1;

    if (free_space < samples) {
        return frames;
    }

    for (int i = 0; i < samples; i++) {
        int idx = (wp + i) % AUDIO_RING_CAPACITY;
        audio_ring[idx] = data[i];
    }

    audio_wpos.store((wp + samples) % AUDIO_RING_CAPACITY,
                     std::memory_order_release);
    return frames;
}

/* ------------------------------------------------------------------ */
/*  Input state & poll callbacks                                       */
/* ------------------------------------------------------------------ */

static int16_t input_state_cb_func(unsigned port, unsigned device,
                                   unsigned index, unsigned id) {
    if (port != 0 || device != RETRO_DEVICE_JOYPAD)
        return 0;
    uint32_t bits = joypad_bits.load(std::memory_order_acquire);
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK)
        return static_cast<int16_t>(bits & 0xffff);
    return (bits & (1u << id)) ? 1 : 0;
}

static void input_poll_cb_func() {
    // joypad_bits is already set atomically by Kotlin via nativeSetInput
}

/* ------------------------------------------------------------------ */
/*  JNI functions                                                      */
/* ------------------------------------------------------------------ */

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeInit(
    JNIEnv* env, jclass, jstring sysDir, jstring saveDirObj) {

    const char* sys = env->GetStringUTFChars(sysDir, nullptr);
    if (sys) {
        strncpy(system_dir, sys, sizeof(system_dir) - 1);
        system_dir[sizeof(system_dir) - 1] = 0;
        env->ReleaseStringUTFChars(sysDir, sys);
    }

    const char* sv = env->GetStringUTFChars(saveDirObj, nullptr);
    if (sv) {
        strncpy(save_dir, sv, sizeof(save_dir) - 1);
        save_dir[sizeof(save_dir) - 1] = 0;
        env->ReleaseStringUTFChars(saveDirObj, sv);
    }

    retro_set_environment(environ_cb_func);
    retro_set_video_refresh(video_refresh_cb);
    retro_set_audio_sample_batch(audio_batch_fn);
    retro_set_input_poll(input_poll_cb_func);
    retro_set_input_state(input_state_cb_func);

    retro_init();

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeLoadGame(
    JNIEnv* env, jclass, jstring romPath) {

    const char* path = env->GetStringUTFChars(romPath, nullptr);
    if (!path)
        return JNI_FALSE;

    retro_game_info info;
    memset(&info, 0, sizeof(info));
    info.path = path;

    bool ok = retro_load_game(&info);
    env->ReleaseStringUTFChars(romPath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeRunFrame(JNIEnv*, jclass) {
    retro_run();
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeReset(JNIEnv*, jclass) {
    retro_reset();
}

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeFrameReady(JNIEnv*, jclass) {
    return frame_ready.exchange(false, std::memory_order_acq_rel)
           ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_justgba_emulator_NativeBridge_nativeGetVideoWidth(JNIEnv*, jclass) {
    return video_width;
}

JNIEXPORT jint JNICALL
Java_com_justgba_emulator_NativeBridge_nativeGetVideoHeight(JNIEnv*, jclass) {
    return video_height;
}

JNIEXPORT jint JNICALL
Java_com_justgba_emulator_NativeBridge_nativeReadAudio(
    JNIEnv* env, jclass, jshortArray jbuffer, jint capacity) {
    jshort* dst = env->GetShortArrayElements(jbuffer, nullptr);
    if (!dst) return 0;

    int rp = audio_rpos.load(std::memory_order_acquire);
    int wp = audio_wpos.load(std::memory_order_acquire);

    int avail;
    if (wp >= rp)
        avail = wp - rp;
    else
        avail = (AUDIO_RING_CAPACITY - rp) + wp;

    int to_read = (avail < capacity) ? avail : capacity;

    for (int i = 0; i < to_read; i++) {
        int idx = (rp + i) % AUDIO_RING_CAPACITY;
        dst[i] = audio_ring[idx];
    }

    audio_rpos.store((rp + to_read) % AUDIO_RING_CAPACITY,
                     std::memory_order_release);
    audio_overrun.store(false, std::memory_order_relaxed);

    env->ReleaseShortArrayElements(jbuffer, dst, 0);
    return to_read;
}

JNIEXPORT jint JNICALL
Java_com_justgba_emulator_NativeBridge_nativeAudioReadable(JNIEnv*, jclass) {
    int rp = audio_rpos.load(std::memory_order_acquire);
    int wp = audio_wpos.load(std::memory_order_acquire);
    if (wp >= rp)
        return wp - rp;
    return (AUDIO_RING_CAPACITY - rp) + wp;
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeSetInput(
    JNIEnv*, jclass, jint mask) {
    joypad_bits.store(static_cast<uint32_t>(mask),
                      std::memory_order_release);
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeSetSkipRender(
    JNIEnv*, jclass, jboolean skip) {
    skip_render.store(skip != JNI_FALSE, std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeSetFastForwardSpeed(
    JNIEnv*, jclass, jfloat multiplier) {
    ff_speed_multiplier.store(multiplier, std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeSetMuteFastForwardAudio(
    JNIEnv*, jclass, jboolean mute) {
    mute_ff_audio.store(mute != JNI_FALSE, std::memory_order_relaxed);
}

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeBatterySave(
    JNIEnv* env, jclass, jstring savePath) {
    const char* path = env->GetStringUTFChars(savePath, nullptr);
    if (!path)
        return JNI_FALSE;

    size_t size = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void* data = retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (!data || size == 0) {
        env->ReleaseStringUTFChars(savePath, path);
        return JNI_FALSE;
    }

    FILE* f = fopen(path, "wb");
    if (!f) {
        env->ReleaseStringUTFChars(savePath, path);
        return JNI_FALSE;
    }

    size_t written = fwrite(data, 1, size, f);
    fclose(f);
    env->ReleaseStringUTFChars(savePath, path);
    return (written == size) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeBatteryLoad(
    JNIEnv* env, jclass, jstring savePath) {
    const char* path = env->GetStringUTFChars(savePath, nullptr);
    if (!path)
        return JNI_FALSE;

    size_t size = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void* data = retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (!data || size == 0) {
        env->ReleaseStringUTFChars(savePath, path);
        return JNI_FALSE;
    }

    FILE* f = fopen(path, "rb");
    if (!f) {
        env->ReleaseStringUTFChars(savePath, path);
        return JNI_FALSE;
    }

    size_t read = fread(data, 1, size, f);
    fclose(f);
    env->ReleaseStringUTFChars(savePath, path);
    return (read > 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeStateSave(
    JNIEnv* env, jclass, jstring savePath) {
    const char* path = env->GetStringUTFChars(savePath, nullptr);
    if (!path)
        return JNI_FALSE;

    size_t size = retro_serialize_size();
    if (size == 0) {
        env->ReleaseStringUTFChars(savePath, path);
        return JNI_FALSE;
    }

    void* buffer = malloc(size);
    if (!buffer) {
        env->ReleaseStringUTFChars(savePath, path);
        return JNI_FALSE;
    }

    bool ok = retro_serialize(buffer, size);
    if (!ok) {
        free(buffer);
        env->ReleaseStringUTFChars(savePath, path);
        return JNI_FALSE;
    }

    FILE* f = fopen(path, "wb");
    if (!f) {
        free(buffer);
        env->ReleaseStringUTFChars(savePath, path);
        return JNI_FALSE;
    }

    size_t written = fwrite(buffer, 1, size, f);
    fclose(f);
    free(buffer);
    env->ReleaseStringUTFChars(savePath, path);
    return (written == size) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeStateLoad(
    JNIEnv* env, jclass, jstring savePath) {
    const char* path = env->GetStringUTFChars(savePath, nullptr);
    if (!path)
        return JNI_FALSE;

    size_t expected = retro_serialize_size();
    if (expected == 0) {
        env->ReleaseStringUTFChars(savePath, path);
        return JNI_FALSE;
    }

    FILE* f = fopen(path, "rb");
    if (!f) {
        env->ReleaseStringUTFChars(savePath, path);
        return JNI_FALSE;
    }

    fseek(f, 0, SEEK_END);
    long file_size = ftell(f);
    fseek(f, 0, SEEK_SET);

    if (file_size <= 0 || static_cast<size_t>(file_size) != expected) {
        fclose(f);
        env->ReleaseStringUTFChars(savePath, path);
        return JNI_FALSE;
    }

    void* buffer = malloc(expected);
    if (!buffer) {
        fclose(f);
        env->ReleaseStringUTFChars(savePath, path);
        return JNI_FALSE;
    }

    size_t read = fread(buffer, 1, expected, f);
    fclose(f);

    bool ok = false;
    if (read == expected) {
        ok = retro_unserialize(buffer, expected);
    }

    free(buffer);
    env->ReleaseStringUTFChars(savePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeSetSurface(JNIEnv* env, jclass, jobject surface) {
    if (native_window) {
        ANativeWindow_release(native_window);
        native_window = nullptr;
    }
    if (surface) {
        native_window = ANativeWindow_fromSurface(env, surface);
    }
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeDeinit(JNIEnv*, jclass) {
    retro_unload_game();
    retro_deinit();

    if (native_window) {
        ANativeWindow_release(native_window);
        native_window = nullptr;
    }
    frame_ready.store(false, std::memory_order_relaxed);
    audio_wpos.store(0, std::memory_order_relaxed);
    audio_rpos.store(0, std::memory_order_relaxed);
    joypad_bits.store(0, std::memory_order_relaxed);
}

} // extern "C"
