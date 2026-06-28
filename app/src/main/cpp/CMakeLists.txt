cmake_minimum_required(VERSION 3.22)
project("justgba" C CXX ASM)

set(GPSP_DIR /home/carter/.local/src/gpsp)
set(LIBRETRO_COMM_DIR ${GPSP_DIR}/libretro/libretro-common)

add_definitions(
    -D__LIBRETRO__
    -DFRONTEND_SUPPORTS_RGB565
    -DARM64_ARCH
    -DMMAP_JIT_CACHE
    -DHAVE_DYNAREC
    -DINLINE=inline
)

include_directories(
    ${GPSP_DIR}
    ${GPSP_DIR}/libretro
    ${LIBRETRO_COMM_DIR}/include
)

set(GPSP_SOURCES_C
    ${GPSP_DIR}/main.c
    ${GPSP_DIR}/gba_memory.c
    ${GPSP_DIR}/savestate.c
    ${GPSP_DIR}/input.c
    ${GPSP_DIR}/sound.c
    ${GPSP_DIR}/cheats.c
    ${GPSP_DIR}/memmap.c
    ${GPSP_DIR}/serial.c
    ${GPSP_DIR}/gbp.c
    ${GPSP_DIR}/rfu.c
    ${GPSP_DIR}/serial_proto.c
    ${GPSP_DIR}/libretro/libretro.c
    ${GPSP_DIR}/gba_cc_lut.c
    ${GPSP_DIR}/cpu_threaded.c
    ${LIBRETRO_COMM_DIR}/compat/compat_posix_string.c
    ${LIBRETRO_COMM_DIR}/compat/compat_strl.c
    ${LIBRETRO_COMM_DIR}/compat/compat_strcasestr.c
    ${LIBRETRO_COMM_DIR}/compat/fopen_utf8.c
    ${LIBRETRO_COMM_DIR}/encodings/encoding_utf.c
    ${LIBRETRO_COMM_DIR}/file/file_path.c
    ${LIBRETRO_COMM_DIR}/file/file_path_io.c
    ${LIBRETRO_COMM_DIR}/streams/file_stream.c
    ${LIBRETRO_COMM_DIR}/string/stdstring.c
    ${LIBRETRO_COMM_DIR}/time/rtime.c
    ${LIBRETRO_COMM_DIR}/vfs/vfs_implementation.c
)

set(GPSP_SOURCES_CC
    ${GPSP_DIR}/video.cc
    ${GPSP_DIR}/cpu.cc
)

set(GPSP_SOURCES_ASM
    ${GPSP_DIR}/bios_data.S
    ${GPSP_DIR}/arm/arm64_stub.S
)

add_library(justgba SHARED
    jni_bridge.cpp
    ${GPSP_SOURCES_C}
    ${GPSP_SOURCES_CC}
    ${GPSP_SOURCES_ASM}
)

target_link_options(justgba PRIVATE -Wl,-z,max-page-size=16384)
target_link_libraries(justgba PRIVATE log android)
